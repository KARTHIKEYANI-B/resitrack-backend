package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.service.ResidentPaymentSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Resident Paid/Unpaid Detail — new feature under
 * Admin/Super Admin → Payment Management.
 *
 * Exposes a single read-only endpoint that returns a Financial Year
 * (April → March) resident × month payment matrix, built entirely from
 * existing payment records via ResidentPaymentSummaryService (which itself
 * reuses the same resident scope and PAID-payment query already used by
 * Dashboard, Maintenance Summary, Financial Summary, and Payment Management).
 *
 * This controller does not modify, extend, or duplicate any existing
 * endpoint — it is additive only.
 */
@RestController
@RequestMapping("/admin/resident-payment-detail")
@RequiredArgsConstructor
public class ResidentPaymentDetailController {

    private final ResidentPaymentSummaryService residentPaymentSummaryService;


    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getResidentPaymentDetail(
            @RequestParam(required = false) Integer fyStartYear) {

        int year = (fyStartYear != null)
                ? fyStartYear
                : residentPaymentSummaryService.getCurrentFinancialYearStart();

        return ResponseEntity.ok(ApiResponse.success(
                residentPaymentSummaryService.getResidentPaymentDetail(year)));
    }
}