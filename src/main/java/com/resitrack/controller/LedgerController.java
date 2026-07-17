package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Resident;
import com.resitrack.service.LedgerService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Owner / Family Member "Ledger Account" — Requirement #2.
 *
 * Available to both Owner and Family Member logins: family members
 * resolve to their linked owner's resident record first (the same
 * pattern already used by PaymentController.getMyPayments /
 * ReceiptController.getMyReceipts), so both see the same shared-flat
 * ledger.
 */
@RestController
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService   ledgerService;
    private final ResidentService residentService;

    @GetMapping("/user/ledger")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLedger(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        int fyStartYear = LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        int resolvedYear = (year != null) ? year : fyStartYear;

        return ResponseEntity.ok(ApiResponse.success(
                ledgerService.getResidentLedger(owner.getId(), resolvedYear, month)));
    }
}
