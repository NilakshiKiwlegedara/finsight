package com.finsight_backend.dto;

import java.math.BigDecimal;

public record MonthlyTrend(String month, BigDecimal income, BigDecimal expenses, BigDecimal netAmount) {}
