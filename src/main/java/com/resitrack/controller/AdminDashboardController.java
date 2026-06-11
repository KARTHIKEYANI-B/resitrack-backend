package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        int y = (year  != null && year  > 0) ? year  : LocalDate.now().getYear();
        int m = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAdminStats(y, m)));
    }

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<List<MonthlyChartDTO>>> getChart(
            @RequestParam(defaultValue = "0") int year) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getYearlyChart(year)));
    }
}