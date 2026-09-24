package com.finsight_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(String month, BigDecimal currentBalance, BigDecimal monthlyIncome,
                                 BigDecimal monthlyExpenses, long activeBudgetCount, long totalBudgetCount,
                                 List<RecentTransactionResponse> recentTransactions,
                                 List<BudgetOverview> budgetOverview, List<CategorySpending> categorySpending) {}
