package com.finsight_backend.controller;

import com.finsight_backend.dto.AnalyticsResponse;
import com.finsight_backend.service.FinancialReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final FinancialReportService reports;

    public AnalyticsController(FinancialReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public AnalyticsResponse analytics(@RequestParam(defaultValue = "6") int months) {
        return reports.analytics(months);
    }
}
