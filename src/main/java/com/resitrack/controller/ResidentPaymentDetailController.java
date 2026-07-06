package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.service.ResidentPaymentSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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