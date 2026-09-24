package com.finsight_backend;

import com.finsight_backend.entity.*;
import com.finsight_backend.enums.TransactionType;
import com.finsight_backend.repository.*;
import com.finsight_backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class UserOwnershipTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired BudgetRepository budgets;
    @Autowired JwtService jwt;
    private MockMvc mvc;
    private User alice, bob;
    private Category aliceCategory, bobCategory, legacyCategory;
    private Transaction aliceTransaction, bobTransaction, legacyTransaction;
    private Budget aliceBudget, bobBudget, legacyBudget;
    private String aliceToken, bobToken;

    @BeforeEach
    void setUp() {
        budgets.deleteAll();
        transactions.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        alice = user("alice@example.test");
        bob = user("bob@example.test");
        aliceCategory = category(alice, "Alice category");
        bobCategory = category(bob, "Bob category");
        legacyCategory = category(null, "Legacy category");
        aliceTransaction = transaction(alice, aliceCategory);
        bobTransaction = transaction(bob, bobCategory);
        legacyTransaction = transaction(null, legacyCategory);
        aliceBudget = budget(alice, aliceCategory);
        bobBudget = budget(bob, bobCategory);
        legacyBudget = budget(null, legacyCategory);
        aliceToken = "Bearer " + jwt.generateToken(alice.getEmail());
        bobToken = "Bearer " + jwt.generateToken(bob.getEmail());
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void listsOnlyIncludeCurrentUsersRecordsAndNeverLegacyRows() throws Exception {
        for (String token : new String[]{aliceToken, bobToken}) {
            boolean isAlice = token.equals(aliceToken);
            assertList("categories", token, isAlice ? aliceCategory.getId() : bobCategory.getId());
            assertList("transactions", token, isAlice ? aliceTransaction.getId() : bobTransaction.getId());
            assertList("budgets", token, isAlice ? aliceBudget.getId() : bobBudget.getId());
        }
        assertEquals(3, categories.count());
        assertEquals(3, transactions.count());
        assertEquals(3, budgets.count());
    }

    @Test
    void crossUserAndLegacyGetUpdateDeleteAreNotFound() throws Exception {
        for (Long id : new Long[]{bobTransaction.getId(), legacyTransaction.getId(), Long.MAX_VALUE}) {
            assertInaccessible("transactions", id, transactionBody(aliceCategory.getId()));
        }
        for (Long id : new Long[]{bobBudget.getId(), legacyBudget.getId(), Long.MAX_VALUE}) {
            assertInaccessible("budgets", id, budgetBody(aliceCategory.getId()));
        }
        assertEquals(3, transactions.count());
        assertEquals(3, budgets.count());
        assertEquals(0, transactions.findById(bobTransaction.getId()).orElseThrow()
                .getAmount().compareTo(new BigDecimal("10")));
        assertEquals(0, budgets.findById(bobBudget.getId()).orElseThrow()
                .getAmount().compareTo(new BigDecimal("10")));
    }

    @Test
    void foreignLegacyAndMissingCategoriesCannotBeUsedOnCreateOrUpdate() throws Exception {
        for (Long id : new Long[]{bobCategory.getId(), legacyCategory.getId(), Long.MAX_VALUE}) {
            for (String resource : new String[]{"transactions", "budgets"}) {
                String body = resource.equals("transactions") ? transactionBody(id) : budgetBody(id);
                Long ownedId = resource.equals("transactions") ? aliceTransaction.getId() : aliceBudget.getId();
                mvc.perform(post("/api/" + resource).header("Authorization", aliceToken)
                                .contentType("application/json").content(body)).andExpect(status().isNotFound());
                mvc.perform(put("/api/" + resource + "/" + ownedId).header("Authorization", aliceToken)
                                .contentType("application/json").content(body)).andExpect(status().isNotFound());
            }
        }
        assertEquals(3, transactions.count());
        assertEquals(3, budgets.count());
        assertEquals(aliceCategory.getId(), transactions.findById(aliceTransaction.getId()).orElseThrow().getCategory().getId());
        assertEquals(0, transactions.findById(aliceTransaction.getId()).orElseThrow().getAmount().compareTo(new BigDecimal("10")));
        assertEquals(0, budgets.findById(aliceBudget.getId()).orElseThrow().getAmount().compareTo(new BigDecimal("10")));
    }

    @Test
    void createIgnoresSuppliedOwnerAndIdAndDoesNotExposeUsersOrPasswords() throws Exception {
        String ownership = "\"id\":" + bobCategory.getId() + ",\"userId\":" + bob.getId()
                + ",\"user\":{\"id\":" + bob.getId() + ",\"password\":\"ignored\"},";
        String categoryBody = "{" + ownership + "\"name\":\"New\",\"type\":\"EXPENSE\"}";
        long createdCategory = create("categories", categoryBody);
        assertNotEquals(bobCategory.getId(), createdCategory);
        assertTrue(categories.findByIdAndUserId(createdCategory, alice.getId()).isPresent());
        assertEquals("Bob category", categories.findById(bobCategory.getId()).orElseThrow().getName());
        for (String resource : new String[]{"transactions", "budgets"}) {
            String body = resource.equals("transactions") ? transactionBody(aliceCategory.getId()) : budgetBody(aliceCategory.getId());
            Long victimId = resource.equals("transactions") ? bobTransaction.getId() : bobBudget.getId();
            body = "{\"id\":" + victimId + ",\"userId\":" + bob.getId()
                    + ",\"user\":{\"id\":" + bob.getId() + "}," + body.substring(1);
            long created = create(resource, body);
            assertNotEquals(victimId, created);
            assertTrue(resource.equals("transactions")
                    ? transactions.findByIdAndUserId(created, alice.getId()).isPresent()
                    : budgets.findByIdAndUserId(created, alice.getId()).isPresent());
        }
    }

    @Test
    void ownerCanGetUpdateAndDeleteWithoutTransferringOwnership() throws Exception {
        Category second = category(alice, "Second category");
        for (String resource : new String[]{"transactions", "budgets"}) {
            Long id = resource.equals("transactions") ? aliceTransaction.getId() : aliceBudget.getId();
            String body = resource.equals("transactions") ? transactionBody(second.getId()) : budgetBody(second.getId());
            body = "{\"user\":{\"id\":" + bob.getId() + "},\"userId\":" + bob.getId() + "," + body.substring(1);
            String url = "/api/" + resource + "/" + id;
            mvc.perform(get(url).header("Authorization", aliceToken)).andExpect(status().isOk());
            mvc.perform(put(url).header("Authorization", aliceToken).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.category.id").value(second.getId()))
                    .andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.category.user").doesNotExist());
            mvc.perform(get(url).header("Authorization", bobToken)).andExpect(status().isNotFound());
            mvc.perform(delete(url).header("Authorization", aliceToken)).andExpect(status().isOk());
            mvc.perform(get(url).header("Authorization", aliceToken)).andExpect(status().isNotFound());
        }
    }

    @Test
    void missingCategoryIsBadRequestAndFinancialEndpointsRequireJwt() throws Exception {
        for (String resource : new String[]{"transactions", "budgets", "categories"}) {
            mvc.perform(get("/api/" + resource)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/" + resource).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            if (!resource.equals("categories")) {
                for (String body : new String[]{"{}", "{\"category\":{}}"}) {
                    mvc.perform(post("/api/" + resource).header("Authorization", aliceToken)
                                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
                }
            }
        }
    }

    private void assertList(String resource, String token, Long expectedId) throws Exception {
        mvc.perform(get("/api/" + resource).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(expectedId))
                .andExpect(jsonPath("$[0].user").doesNotExist()).andExpect(jsonPath("$[0].password").doesNotExist());
    }

    private void assertInaccessible(String resource, Long id, String body) throws Exception {
        String url = "/api/" + resource + "/" + id;
        mvc.perform(get(url).header("Authorization", aliceToken)).andExpect(status().isNotFound());
        mvc.perform(put(url).header("Authorization", aliceToken).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization", aliceToken)).andExpect(status().isNotFound());
    }

    private long create(String resource, String body) throws Exception {
        String json = mvc.perform(post("/api/" + resource).header("Authorization", aliceToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.category.user").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
    }

    private String transactionBody(Long category) {
        return "{\"amount\":25,\"type\":\"EXPENSE\",\"date\":\"2026-09-24\",\"description\":\"Updated\",\"category\":{\"id\":" + category + "}}";
    }

    private String budgetBody(Long category) {
        return "{\"amount\":25,\"month\":9,\"year\":2026,\"category\":{\"id\":" + category + "}}";
    }

    private User user(String email) {
        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setPassword("test-only-not-a-real-password");
        return users.save(user);
    }

    private Category category(User user, String name) {
        Category category = new Category();
        category.setName(name);
        category.setType(TransactionType.EXPENSE);
        category.setUser(user);
        return categories.save(category);
    }

    private Transaction transaction(User user, Category category) {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("10"));
        transaction.setType(TransactionType.EXPENSE);
        transaction.setDate(LocalDate.of(2026, 9, 24));
        transaction.setCategory(category);
        transaction.setUser(user);
        return transactions.save(transaction);
    }

    private Budget budget(User user, Category category) {
        Budget budget = new Budget();
        budget.setAmount(new BigDecimal("10"));
        budget.setMonth(9);
        budget.setYear(2026);
        budget.setCategory(category);
        budget.setUser(user);
        return budgets.save(budget);
    }
}
