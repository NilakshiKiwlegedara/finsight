package com.finsight_backend;

import com.finsight_backend.entity.Category;
import com.finsight_backend.entity.User;
import com.finsight_backend.enums.TransactionType;
import com.finsight_backend.repository.*;
import com.finsight_backend.service.DefaultCategoryService;
import com.finsight_backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class ApiQualityTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired BudgetRepository budgets;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean DefaultCategoryService defaults;
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    private User alice;
    private String token;
    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        budgets.deleteAll();
        transactions.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        register("alice@example.test").andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
        alice = users.findByEmail("alice@example.test").orElseThrow();
        token = "Bearer " + jwt.generateToken(alice.getEmail());
        categoryId = categories.findAllByUserId(alice.getId()).getFirst().getId();
    }

    @Test
    void registrationCreatesExactlySixPrivateDefaultsForEachUser() throws Exception {
        register("bob@example.test").andExpect(status().isOk());
        User bob = users.findByEmail("bob@example.test").orElseThrow();
        Set<Long> ids = new HashSet<>();
        for (User user : List.of(alice, bob)) {
            List<Category> owned = categories.findAllByUserId(user.getId());
            assertEquals(6, owned.size());
            assertEquals(Set.of("Food", "Transport", "Shopping", "Bills", "Entertainment", "Salary"),
                    new HashSet<>(owned.stream().map(Category::getName).toList()));
            for (Category category : owned) {
                assertTrue(category.isDefault());
                assertTrue(ids.add(category.getId()));
                assertEquals(category.getName().equals("Salary") ? TransactionType.INCOME : TransactionType.EXPENSE,
                        category.getType());
            }
            mvc.perform(get("/api/categories").header("Authorization", "Bearer " + jwt.generateToken(user.getEmail())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$[0].user").doesNotExist());
        }
        assertTrue(encoder.matches("test-password", alice.getPassword()));
    }

    @Test
    void defaultInitializationCanBeRetriedIncludingConcurrently() throws Exception {
        defaults.createForUser(alice);
        defaults.createForUser(alice);
        assertEquals(6, categories.findAllByUserId(alice.getId()).size());
        categories.deleteAll();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Retry start timed out");
                defaults.createForUser(alice);
                return null;
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertEquals(6, categories.findAllByUserId(alice.getId()).size());
    }

    @Test
    void duplicateRegistrationReturns409WithoutAddingDefaults() throws Exception {
        assertError(register(alice.getEmail()), 409)
                .andExpect(jsonPath("$.message").value("Email already registered"));
        assertEquals(1, users.count());
        assertEquals(6, categories.count());
    }

    @Test
    void failedDefaultCreationRollsBackRegistrationAndHidesExceptionDetails() throws Exception {
        doThrow(new IllegalStateException("private database detail")).when(defaults).createForUser(any());
        String response = assertError(register("failed@example.test"), 500)
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private database detail"));
        assertFalse(users.existsByEmail("failed@example.test"));
        assertEquals(6, categories.count());
    }

    @Test
    void customCategoryCannotBecomeDefaultEvenWhenClientSetsFlags() throws Exception {
        mvc.perform(post("/api/categories").header("Authorization", token).contentType("application/json")
                        .content("{\"name\":\"Custom\",\"type\":\"EXPENSE\",\"default\":true,\"isDefault\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.default").value(false));
        defaults.createForUser(alice);
        assertEquals(7, categories.count());
        assertEquals(6, categories.findAllByUserId(alice.getId()).stream().filter(Category::isDefault).count());
    }

    @Test
    void registrationValidatesRequiredFieldsEmailAndPasswordWithoutEchoingValues() throws Exception {
        Map<String, Object> valid = new HashMap<>(Map.of("name", "Test", "email", "new@example.test", "password", "test-password"));
        for (String field : List.of("name", "email", "password")) {
            for (Object value : Arrays.asList(null, "", "   ")) {
                var body = new HashMap<>(valid);
                body.put(field, value);
                validation("/api/auth/register", body, field);
            }
        }
        for (String password : List.of("short", "x".repeat(73), "é".repeat(37))) {
            var body = new HashMap<>(valid);
            body.put("password", password);
            String response = assertError(postJson("/api/auth/register", body), 400).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains(password));
        }
        var body = new HashMap<>(valid);
        body.put("email", "not-an-email");
        validation("/api/auth/register", body, "email");
        assertEquals(1, users.count());
    }

    @Test
    void badLoginAndMissingOrInvalidJwtHaveConsistent401Responses() throws Exception {
        for (String email : List.of(alice.getEmail(), "unknown@example.test")) {
            assertError(postJson("/api/auth/login", Map.of("email", email, "password", "wrong-password")), 401)
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
        assertError(mvc.perform(get("/api/categories")), 401);
        String response = assertError(mvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer private-invalid-token")), 401)
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private-invalid-token"));
        validation("/api/auth/login", Map.of("email", "bad", "password", ""), "email");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", alice.getEmail(), "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }

    @Test
    void financialValidationRejectsMissingFieldsRangesAndInvalidPrecision() throws Exception {
        var transaction = new HashMap<String, Object>(Map.of("amount", 25, "type", "EXPENSE", "date", "2026-09-24",
                "category", Map.of("id", categoryId)));
        var budget = new HashMap<String, Object>(Map.of("amount", 25, "month", 9, "year", 2026,
                "category", Map.of("id", categoryId)));
        for (var item : Map.of("transactions", transaction, "budgets", budget).entrySet()) {
            String path = "/api/" + item.getKey();
            for (String field : item.getValue().keySet()) {
                var body = new HashMap<>(item.getValue());
                body.remove(field);
                validation(path, body, field);
            }
            for (Object amount : List.of(0, -1, 0.001, 10000000000L)) {
                var body = new HashMap<>(item.getValue());
                body.put("amount", amount);
                validation(path, body, "amount");
            }
            var body = new HashMap<>(item.getValue());
            body.put("category", Map.of());
            validation(path, body, "category.id");
            body.put("category", Map.of("id", 0));
            validation(path, body, "category.id");
            mvc.perform(post(path).header("Authorization", token).contentType("application/json")
                            .content(json.writeValueAsString(item.getValue())))
                    .andExpect(status().isOk());
        }
        for (int month : new int[]{0, 13}) {
            var body = new HashMap<>(budget); body.put("month", month);
            validation("/api/budgets", body, "month");
        }
        for (int year : new int[]{1899, 2101}) {
            var body = new HashMap<>(budget); body.put("year", year);
            validation("/api/budgets", body, "year");
        }
        validation("/api/categories", Map.of("name", " ", "type", "EXPENSE"), "name");
        validation("/api/categories", Map.of("name", "Test"), "type");
        assertEquals(1, transactions.count());
        assertEquals(1, budgets.count());
    }

    @Test
    void updateValidationAndOwned404UseConsistentErrors() throws Exception {
        for (String resource : List.of("transactions", "budgets")) {
            Map<String, Object> valid = resource.equals("transactions")
                    ? Map.of("amount", 10, "type", "EXPENSE", "date", "2026-09-24", "category", Map.of("id", categoryId))
                    : Map.of("amount", 10, "month", 9, "year", 2026, "category", Map.of("id", categoryId));
            var createResponse = postJson("/api/" + resource, valid).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Number id = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");
            var invalid = new HashMap<>(valid); invalid.put("amount", 0);
            assertError(mvc.perform(put("/api/" + resource + "/" + id).header("Authorization", token)
                    .contentType("application/json").content(json.writeValueAsString(invalid))), 400);
            for (var request : List.of(get("/api/" + resource + "/999999"), delete("/api/" + resource + "/999999"),
                    put("/api/" + resource + "/999999").contentType("application/json").content(json.writeValueAsString(valid)))) {
                assertError(mvc.perform(request.header("Authorization", token)), 404);
            }
        }
    }

    @Test
    void malformedJsonEnumDateAndIdReturnSanitized400() throws Exception {
        for (String body : List.of("{bad-json", "{\"name\":\"Test\",\"type\":\"private-invalid-enum\"}")) {
            assertError(mvc.perform(post("/api/categories").header("Authorization", token)
                    .contentType("application/json").content(body)), 400);
        }
        assertError(postJson("/api/transactions", Map.of("amount", 1, "type", "EXPENSE", "date", "invalid-date",
                "category", Map.of("id", categoryId))), 400);
        assertError(mvc.perform(get("/api/transactions/not-a-number").header("Authorization", token)), 400);
    }

    private ResultActions register(String email) throws Exception {
        return postJson("/api/auth/register", Map.of("name", "Test", "email", email, "password", "test-password"));
    }

    private ResultActions postJson(String path, Map<String, Object> body) throws Exception {
        var request = post(path).contentType("application/json").content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", token);
        return mvc.perform(request);
    }

    private void validation(String path, Map<String, Object> body, String field) throws Exception {
        assertError(postJson(path, body), 400).andExpect(jsonPath("$.errors['" + field + "']").isString());
    }

    private ResultActions assertError(ResultActions result, int status) throws Exception {
        return result.andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
    }
}
