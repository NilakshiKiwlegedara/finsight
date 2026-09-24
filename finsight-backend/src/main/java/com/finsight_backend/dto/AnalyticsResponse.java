package com.finsight_backend.dto;

import java.util.List;

public record AnalyticsResponse(String startMonth, String endMonth, int months,
                                 List<MonthlyTrend> monthlyTrends, List<CategorySpending> categorySpending) {}
