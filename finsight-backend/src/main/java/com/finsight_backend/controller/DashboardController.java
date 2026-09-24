package com.finsight_backend.controller;

import com.finsight_backend.dto.DashboardResponse;
import com.finsight_backend.service.FinancialReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final FinancialReportService reports;

    public DashboardController(FinancialReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        return reports.dashboard();
    }
}
