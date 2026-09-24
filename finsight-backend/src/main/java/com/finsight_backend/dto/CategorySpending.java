package com.finsight_backend.dto;

import java.math.BigDecimal;

public record CategorySpending(CategorySummary category, BigDecimal amountSpent) {}
