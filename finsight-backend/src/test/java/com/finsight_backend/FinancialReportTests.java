package com.finsight_backend;

import com.finsight_backend.entity.*;
import com.finsight_backend.enums.TransactionType;
import com.finsight_backend.repository.*;
import com.finsight_backend.service.JwtService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.location=classpath:/application-test.properties",
        "spring.datasource.url=jdbc:h2:mem:report-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,MONTH",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@Import(FinancialReportTests.FixedClock.class)
class FinancialReportTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired BudgetRepository budgets;
    @Autowired JwtService jwt;
    @Autowired EntityManagerFactory entityManagerFactory;
    private MockMvc mvc;
    private User alice, bob;
    private Category food, transport, salary, unused, bobFood, legacyCategory;
    private String token;

    @BeforeEach
    void setUp() {
        budgets.deleteAll(); transactions.deleteAll(); categories.deleteAll(); users.deleteAll();
        alice = user("alice@example.test"); bob = user("bob@example.test");
        food = category(alice, "Food", TransactionType.EXPENSE);
        transport = category(alice, "Transport", TransactionType.EXPENSE);
        salary = category(alice, "Salary", TransactionType.INCOME);
        unused = category(alice, "Unused", TransactionType.EXPENSE);
        bobFood = category(bob, "Food", TransactionType.EXPENSE);
        legacyCategory = category(null, "Legacy", TransactionType.EXPENSE);
        token = "Bearer " + jwt.generateToken(alice.getEmail());
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void dashboardSeparatesAllTimeAndCurrentMonthTotals() throws Exception {
        populate();
        dashboard().andExpect(status().isOk()).andExpect(jsonPath("$.month").value("2026-01"))
                .andExpect(jsonPath("$.currentBalance").value(1558.70))
                .andExpect(jsonPath("$.monthlyIncome").value(1015.00))
                .andExpect(jsonPath("$.monthlyExpenses").value(120.30))
                .andExpect(jsonPath("$.activeBudgetCount").value(4))
                .andExpect(jsonPath("$.totalBudgetCount").value(6))
                .andExpect(jsonPath("$.recentTransactions.length()").value(5))
                .andExpect(jsonPath("$.recentTransactions[0].user").doesNotExist())
                .andExpect(jsonPath("$.recentTransactions[0].category.user").doesNotExist())
                .andExpect(jsonPath("$.recentTransactions[0].password").doesNotExist());
    }

    @Test
    void spendingAndBudgetProgressMatchUserCategoryMonthAndYear() throws Exception {
        populate();
        dashboard().andExpect(status().isOk())
                .andExpect(jsonPath("$.categorySpending.length()").value(2))
                .andExpect(jsonPath("$.categorySpending[0].category.id").value(food.getId()))
                .andExpect(jsonPath("$.categorySpending[0].amountSpent").value(100.30))
                .andExpect(jsonPath("$.categorySpending[1].amountSpent").value(20.00))
                .andExpect(jsonPath("$.budgetOverview.length()").value(4))
                .andExpect(jsonPath("$.budgetOverview[0].budgetAmount").value(100.00))
                .andExpect(jsonPath("$.budgetOverview[0].amountSpent").value(100.30))
                .andExpect(jsonPath("$.budgetOverview[0].remainingAmount").value(-0.30))
                .andExpect(jsonPath("$.budgetOverview[0].percentageUsed").value(100.30))
                .andExpect(jsonPath("$.budgetOverview[1].remainingAmount").value(280.00))
                .andExpect(jsonPath("$.budgetOverview[1].percentageUsed").value(6.67))
                .andExpect(jsonPath("$.budgetOverview[2].amountSpent").value(0.00))
                .andExpect(jsonPath("$.budgetOverview[2].remainingAmount").value(50.00))
                .andExpect(jsonPath("$.budgetOverview[2].percentageUsed").value(0.00))
                .andExpect(jsonPath("$.budgetOverview[3].percentageUsed").value(0.00));
    }

    @Test
    void recentTransactionsAreLatestFiveByDateThenId() throws Exception {
        List<Transaction> inserted = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            inserted.add(transaction(alice, food, TransactionType.EXPENSE, "1.00", "2026-01-10"));
        }
        transaction(alice, food, TransactionType.EXPENSE, "2.00", "2025-12-31"); // Higher ID, older date.
        transaction(bob, bobFood, TransactionType.EXPENSE, "999.00", "2026-02-01");
        var response = dashboard().andExpect(status().isOk()).andExpect(jsonPath("$.recentTransactions.length()").value(5));
        for (int i = 0; i < 5; i++) {
            response.andExpect(jsonPath("$.recentTransactions[" + i + "].id").value(inserted.get(6 - i).getId()));
        }
    }

    @Test
    void analyticsReturnsSixOrderedMonthsIncludingZeroesAcrossYearBoundary() throws Exception {
        populate();
        mvc.perform(get("/api/analytics").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.startMonth").value("2025-08"))
                .andExpect(jsonPath("$.endMonth").value("2026-01"))
                .andExpect(jsonPath("$.months").value(6))
                .andExpect(jsonPath("$.monthlyTrends.length()").value(6))
                .andExpect(jsonPath("$.monthlyTrends[0].month").value("2025-08"))
                .andExpect(jsonPath("$.monthlyTrends[0].income").value(100.00))
                .andExpect(jsonPath("$.monthlyTrends[0].expenses").value(30.00))
                .andExpect(jsonPath("$.monthlyTrends[0].netAmount").value(70.00))
                .andExpect(jsonPath("$.monthlyTrends[1].month").value("2025-09"))
                .andExpect(jsonPath("$.monthlyTrends[1].income").value(0.00))
                .andExpect(jsonPath("$.monthlyTrends[1].expenses").value(0.00))
                .andExpect(jsonPath("$.monthlyTrends[1].netAmount").value(0.00))
                .andExpect(jsonPath("$.monthlyTrends[4].netAmount").value(160.00))
                .andExpect(jsonPath("$.monthlyTrends[5].month").value("2026-01"))
                .andExpect(jsonPath("$.monthlyTrends[5].income").value(1015.00))
                .andExpect(jsonPath("$.monthlyTrends[5].expenses").value(120.30))
                .andExpect(jsonPath("$.monthlyTrends[5].netAmount").value(894.70))
                .andExpect(jsonPath("$.categorySpending[0].amountSpent").value(170.30))
                .andExpect(jsonPath("$.categorySpending[1].amountSpent").value(20.00));
    }

    @Test
    void analyticsPeriodIsConfigurableAndInvalidPeriodsUseExisting400Errors() throws Exception {
        populate();
        mvc.perform(get("/api/analytics?months=1").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyTrends.length()").value(1))
                .andExpect(jsonPath("$.categorySpending[0].amountSpent").value(100.30));
        mvc.perform(get("/api/analytics?months=24").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyTrends.length()").value(24));
        for (String value : List.of("0", "-1", "25", "abc")) {
            mvc.perform(get("/api/analytics").param("months", value).header("Authorization", token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.timestamp").isString());
        }
    }

    @Test
    void usersAreIsolatedAndSuppliedUserIdCannotChangeIdentity() throws Exception {
        populate();
        // Bob's large values and unowned legacy values must never affect Alice's totals.
        mvc.perform(get("/api/dashboard").param("userId", bob.getId().toString()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentBalance").value(1558.70));
        String bobToken = "Bearer " + jwt.generateToken(bob.getEmail());
        mvc.perform(get("/api/dashboard").header("Authorization", bobToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyExpenses").value(9000.00))
                .andExpect(jsonPath("$.activeBudgetCount").value(1))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1));
        mvc.perform(get("/api/analytics").param("userId", alice.getId().toString()).header("Authorization", bobToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyTrends[5].income").value(0.00))
                .andExpect(jsonPath("$.monthlyTrends[5].expenses").value(9000.00))
                .andExpect(jsonPath("$.monthlyTrends[5].netAmount").value(-9000.00))
                .andExpect(jsonPath("$.categorySpending.length()").value(1));
    }

    @Test
    void emptyAccountsHaveZeroTotalsEmptyListsAndZeroFilledTrends() throws Exception {
        dashboard().andExpect(status().isOk()).andExpect(jsonPath("$.currentBalance").value(0.00))
                .andExpect(jsonPath("$.monthlyIncome").value(0.00)).andExpect(jsonPath("$.monthlyExpenses").value(0.00))
                .andExpect(jsonPath("$.activeBudgetCount").value(0)).andExpect(jsonPath("$.totalBudgetCount").value(0))
                .andExpect(jsonPath("$.recentTransactions").isEmpty()).andExpect(jsonPath("$.budgetOverview").isEmpty())
                .andExpect(jsonPath("$.categorySpending").isEmpty());
        var result = mvc.perform(get("/api/analytics").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyTrends.length()").value(6)).andExpect(jsonPath("$.categorySpending").isEmpty());
        for (int i = 0; i < 6; i++) {
            result.andExpect(jsonPath("$.monthlyTrends[" + i + "].income").value(0.00))
                    .andExpect(jsonPath("$.monthlyTrends[" + i + "].expenses").value(0.00))
                    .andExpect(jsonPath("$.monthlyTrends[" + i + "].netAmount").value(0.00));
        }
    }

    @Test
    void reportsRequireJwtAuthentication() throws Exception {
        for (String path : List.of("/api/dashboard", "/api/analytics")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
            mvc.perform(get(path).header("Authorization", "Bearer invalid"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void dashboardQueryCountDoesNotGrowWithNumberOfBudgets() throws Exception {
        populate();
        for (int i = 0; i < 30; i++) {
            Category extra = category(alice, "Extra " + i, TransactionType.EXPENSE);
            budget(alice, extra, "25.00", 1, 2026);
            transaction(alice, extra, TransactionType.EXPENSE, "1.00", "2026-01-10");
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        dashboard().andExpect(status().isOk()).andExpect(jsonPath("$.budgetOverview.length()").value(34));
        assertTrue(statistics.getPrepareStatementCount() <= 10,
                "Dashboard should use bounded aggregate/fetch queries, not one query per budget/category");
    }

    private void populate() {
        transaction(alice, salary, TransactionType.INCOME, "50.00", "2025-07-31");
        transaction(alice, food, TransactionType.EXPENSE, "10.00", "2025-07-31");
        transaction(alice, salary, TransactionType.INCOME, "100.00", "2025-08-01");
        transaction(alice, food, TransactionType.EXPENSE, "30.00", "2025-08-01");
        transaction(alice, salary, TransactionType.INCOME, "200.00", "2025-12-31");
        transaction(alice, food, TransactionType.EXPENSE, "40.00", "2025-12-31");
        transaction(alice, salary, TransactionType.INCOME, "1000.00", "2026-01-01");
        transaction(alice, food, TransactionType.EXPENSE, "25.10", "2026-01-05");
        transaction(alice, food, TransactionType.EXPENSE, "10.20", "2026-01-06");
        transaction(alice, food, TransactionType.EXPENSE, "5.00", "2026-01-06");
        transaction(alice, transport, TransactionType.EXPENSE, "20.00", "2026-01-07");
        // Transaction type, not the category label/type, determines income versus expense.
        transaction(alice, food, TransactionType.INCOME, "15.00", "2026-01-08");
        transaction(alice, food, TransactionType.EXPENSE, "60.00", "2026-01-31");
        transaction(alice, salary, TransactionType.INCOME, "500.00", "2026-02-01");
        transaction(alice, food, TransactionType.EXPENSE, "7.00", "2026-02-01");
        transaction(alice, food, TransactionType.EXPENSE, "99.00", "2025-01-01");
        transaction(bob, bobFood, TransactionType.EXPENSE, "9000.00", "2026-01-09");
        transaction(null, legacyCategory, TransactionType.EXPENSE, "8000.00", "2026-01-09");
        budget(alice, food, "100.00", 1, 2026);
        budget(alice, transport, "300.00", 1, 2026);
        budget(alice, unused, "50.00", 1, 2026);
        budget(alice, food, "0.00", 1, 2026); // Legacy zero-amount budget, predating validation.
        budget(alice, food, "500.00", 12, 2025);
        budget(alice, food, "500.00", 1, 2025);
        budget(bob, bobFood, "10000.00", 1, 2026);
        budget(null, legacyCategory, "10000.00", 1, 2026);
    }

    private ResultActions dashboard() throws Exception {
        return mvc.perform(get("/api/dashboard").header("Authorization", token));
    }

    private User user(String email) {
        User user = new User(); user.setName("Test"); user.setEmail(email); user.setPassword("test-only");
        return users.save(user);
    }

    private Category category(User user, String name, TransactionType type) {
        Category category = new Category(); category.setUser(user); category.setName(name); category.setType(type);
        return categories.save(category);
    }

    private Transaction transaction(User user, Category category, TransactionType type, String amount, String date) {
        Transaction transaction = new Transaction(); transaction.setUser(user); transaction.setCategory(category);
        transaction.setType(type); transaction.setAmount(new BigDecimal(amount)); transaction.setDate(LocalDate.parse(date));
        return transactions.save(transaction);
    }

    private void budget(User user, Category category, String amount, int month, int year) {
        Budget budget = new Budget(); budget.setUser(user); budget.setCategory(category);
        budget.setAmount(new BigDecimal(amount)); budget.setMonth(month); budget.setYear(year);
        budgets.save(budget);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary
        Clock testReportingClock() {
            return Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("Asia/Colombo"));
        }
    }
}
