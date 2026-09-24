package com.finsight_backend.service;

import com.finsight_backend.dto.*;
import com.finsight_backend.entity.Category;
import com.finsight_backend.enums.TransactionType;
import com.finsight_backend.repository.BudgetRepository;
import com.finsight_backend.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class FinancialReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public FinancialReportService(TransactionRepository transactions, BudgetRepository budgets,
                                  CurrentUserService currentUser, Clock clock) {
        this.transactions = transactions;
        this.budgets = budgets;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public DashboardResponse dashboard() {
        Long userId = currentUser.getCurrentUser().getId();
        YearMonth month = YearMonth.now(clock);
        var allTime = new EnumMap<TransactionType, BigDecimal>(TransactionType.class);
        transactions.sumByType(userId).forEach(total -> allTime.put(total.getType(), money(total.getTotal())));
        MonthlyTrend current = monthlyTrends(userId, month, month).getFirst();
        List<CategorySpending> spending = categorySpending(userId, month, month);
        Map<Long, BigDecimal> spentByCategory = new HashMap<>();
        spending.forEach(item -> spentByCategory.put(item.category().id(), item.amountSpent()));

        // One bulk category aggregate serves every budget, including duplicate budgets for a category.
        List<BudgetOverview> overview = budgets.findAllByUserIdAndMonthAndYearOrderByIdAsc(
                userId, month.getMonthValue(), month.getYear()).stream().map(budget -> {
                    BigDecimal amount = money(budget.getAmount());
                    BigDecimal spent = spentByCategory.getOrDefault(budget.getCategory().getId(), ZERO);
                    // Pre-validation data may contain zero budgets. Never divide by zero.
                    BigDecimal percentage = amount.signum() > 0
                            ? spent.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP) : ZERO;
                    return new BudgetOverview(budget.getId(), category(budget.getCategory()), amount,
                            spent, amount.subtract(spent), percentage);
                }).toList();

        List<RecentTransactionResponse> recent = transactions.findTop5ByUserIdOrderByDateDescIdDesc(userId)
                .stream().map(transaction -> new RecentTransactionResponse(transaction.getId(), money(transaction.getAmount()),
                        transaction.getType(), transaction.getDate(), transaction.getDescription(),
                        category(transaction.getCategory()))).toList();
        return new DashboardResponse(month.toString(),
                allTime.getOrDefault(TransactionType.INCOME, ZERO).subtract(allTime.getOrDefault(TransactionType.EXPENSE, ZERO)),
                current.income(), current.expenses(), overview.size(), budgets.countByUserId(userId), recent, overview, spending);
    }

    public AnalyticsResponse analytics(int months) {
        if (months < 1 || months > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "months must be between 1 and 24");
        }
        Long userId = currentUser.getCurrentUser().getId();
        YearMonth end = YearMonth.now(clock);
        YearMonth start = end.minusMonths(months - 1L);
        return new AnalyticsResponse(start.toString(), end.toString(), months,
                monthlyTrends(userId, start, end), categorySpending(userId, start, end));
    }

    private List<MonthlyTrend> monthlyTrends(Long userId, YearMonth start, YearMonth end) {
        Map<YearMonth, EnumMap<TransactionType, BigDecimal>> totals = new HashMap<>();
        transactions.sumByMonthAndType(userId, start.atDay(1), end.plusMonths(1).atDay(1)).forEach(row ->
                totals.computeIfAbsent(YearMonth.of(row.getReportYear(), row.getReportMonth()),
                        ignored -> new EnumMap<>(TransactionType.class)).put(row.getType(), money(row.getTotal())));
        List<MonthlyTrend> result = new ArrayList<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            var values = totals.getOrDefault(month, new EnumMap<>(TransactionType.class));
            BigDecimal income = values.getOrDefault(TransactionType.INCOME, ZERO);
            BigDecimal expenses = values.getOrDefault(TransactionType.EXPENSE, ZERO);
            result.add(new MonthlyTrend(month.toString(), income, expenses, income.subtract(expenses)));
        }
        return result;
    }

    private List<CategorySpending> categorySpending(Long userId, YearMonth start, YearMonth end) {
        return transactions.sumByCategory(userId, TransactionType.EXPENSE, start.atDay(1), end.plusMonths(1).atDay(1))
                .stream().map(row -> new CategorySpending(new CategorySummary(row.getCategoryId(), row.getCategoryName(),
                        row.getCategoryType()), money(row.getTotal()))).toList();
    }

    private CategorySummary category(Category category) {
        return new CategorySummary(category.getId(), category.getName(), category.getType());
    }

    private BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
