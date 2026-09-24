package com.finsight_backend.dto;

import java.math.BigDecimal;

public record BudgetOverview(Long id, CategorySummary category, BigDecimal budgetAmount,
                             BigDecimal amountSpent, BigDecimal remainingAmount, BigDecimal percentageUsed) {}
