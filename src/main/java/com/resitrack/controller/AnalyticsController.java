package com.resitrack.controller;

import com.resitrack.dto.AnalyticsDTO;
import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.MonthlyChartDTO;
import com.resitrack.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsDTO>> getSummary(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getAnalyticsSummary(year, month)));
    }

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<List<MonthlyChartDTO>>> getChart(
            @RequestParam(defaultValue = "0") int year) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(analyticsService.buildYearlyChart(year)));
    }

    @GetMapping("/expense-breakdown")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExpenseBreakdown(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(analyticsService.buildExpenseBreakdown(year, month)));
    }

    @GetMapping("/payment-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStats(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        if (year == 0) year = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPaymentStats(year, month)));
    }
}