package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.*;
import com.resitrack.repository.*;
import com.resitrack.service.PaymentService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService        paymentService;
    private final ResidentService       residentService;
    private final PaymentRepository     paymentRepo;
    private final ResidentRepository    residentRepo;
    private final MaintenanceRepository maintenanceRepo;

    @GetMapping("/admin/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDTO>> getAllPayments(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentService.getAllPayments(status));
    }

    // Creates one Payment row per selected billing month — see
    // PaymentService.registerAdminPayment() for the multi-month logic.
    // Always returns a list (length 1 for a single-month selection) so the
    // response shape is consistent regardless of how many months were
    // selected.
    @PostMapping("/admin/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentResponseDTO>>> createAdminPayment(
            @RequestBody AdminPaymentRequest req, Authentication auth) {
        List<PaymentResponseDTO> created = paymentService.registerAdminPayment(req, auth.getName());
        boolean verified = Boolean.TRUE.equals(req.getVerifiedByAdmin());
        String monthWord = created.size() == 1 ? "month" : "months";
        String message = verified
                ? "Payment recorded and verified for " + created.size() + " " + monthWord
                : "Payment recorded for " + created.size() + " " + monthWord + ", pending verification";
        return ResponseEntity.ok(ApiResponse.success(message, created));
    }

    // Auto-fill support for the "Add Payment" form: given a residentId
    // (preferred — from the resident search/select dropdown) or, for
    // backward compatibility, a phone number, returns the resident's name/
    // flat and current monthly maintenance rate so the frontend can
    // pre-fill Amount = rate × selected months.
    // Read-only — see PaymentService.lookupResidentMaintenanceInfo().
    @GetMapping("/admin/payments/resident-maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getResidentMaintenanceInfo(
            @RequestParam(required = false) Long residentId,
            @RequestParam(required = false) String phone) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.lookupResidentMaintenanceInfo(residentId, phone)));
    }

    // Backs the "Record Payment" form's searchable resident dropdown —
    // every active/approved owner with their monthly maintenance amount,
    // so Amount can be auto-calculated as monthlyMaintenanceAmount ×
    // selected months without a separate per-resident lookup call.
    @GetMapping("/admin/payments/eligible-residents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ResidentMaintenanceInfoDTO>>> getEligibleResidentsForPayment() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getEligibleResidentsForPayment()));
    }

    @GetMapping("/admin/payments/tracking-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentTrackingStatsDTO>> getTrackingStats() {
        int    year           = LocalDate.now().getYear();
        int    month          = LocalDate.now().getMonthValue();
        String currentMonthStr = year + "-" + String.format("%02d", month);

        // ── Owner counts ──────────────────────────────────────────────────
        long totalOwners = residentRepo.count();

        long activeFlatOwners  = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.FLAT);
        long activeVillaOwners = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.VILLA);
        long activeOwners      = activeFlatOwners + activeVillaOwners;

        // ── Monthly paid / unpaid ─────────────────────────────────────────
        Long paidRaw    = paymentRepo.countDistinctResidentsPaidByPaymentMonth(currentMonthStr);
        long paidOwners = paidRaw == null ? 0L : paidRaw;

        long pendingVerif  = paymentRepo.countPendingVerification();
        long overdueOwners = paymentRepo.countByPaymentStatusForMonth(
                Payment.PaymentStatus.OVERDUE, currentMonthStr);

        long unpaidOwners = Math.max(0, activeOwners - paidOwners);
        Double collected = paymentRepo.sumPaidAmountByPaymentMonth(currentMonthStr);
        collected = collected == null ? 0.0 : collected;

        Double bankCollected = paymentRepo.sumBankPaidByPaymentMonth(currentMonthStr);
        Double cashCollected = paymentRepo.sumCashPaidByPaymentMonth(currentMonthStr);
        bankCollected = bankCollected == null ? 0.0 : bankCollected;
        cashCollected = cashCollected == null ? 0.0 : cashCollected;

        double collPct = activeOwners > 0 ? (paidOwners * 100.0 / activeOwners) : 0.0;

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        double maintAmountPerOwner = activeMaint
                .map(m -> m.getAmount() != null ? m.getAmount().doubleValue() : 0.0)
                .orElse(0.0);

        int completedMonths = Math.max(0, month - 1); // months that have fully passed this year
        int previousMonth   = month == 1 ? 0 : month - 1; // 0 means nothing to query

        double totalExpectedYTD;
        double totalCollectedYTD;
        double annualPendingDues;

        if (completedMonths == 0 || maintAmountPerOwner == 0.0) {
            // No completed months yet (we're in January) or no maintenance configured
            totalExpectedYTD  = 0.0;
            totalCollectedYTD = 0.0;
            annualPendingDues = 0.0;
        } else {
            // Total that SHOULD have been collected Jan – previousMonth
            totalExpectedYTD = activeOwners * maintAmountPerOwner * completedMonths;

            // Total actually collected Jan – previousMonth (from DB)
            Double ytdRaw = paymentRepo.sumCollectedByYearUpToMonth(year, previousMonth);
            totalCollectedYTD = ytdRaw == null ? 0.0 : ytdRaw;

            // Pending = what was owed but not yet paid
            annualPendingDues = Math.max(0.0, totalExpectedYTD - totalCollectedYTD);
        }

        PaymentTrackingStatsDTO stats = PaymentTrackingStatsDTO.builder()
                .totalRegisteredOwners(totalOwners)
                .totalActiveOwners(activeOwners)
                .paidOwners(paidOwners)
                .unpaidOwners(unpaidOwners)
                .overdueOwners(overdueOwners)
                .pendingVerification(pendingVerif)
                .collectionPercentage(Math.min(100.0, Math.round(collPct * 10.0) / 10.0))
                .totalCollectedThisMonth(collected)
                .bankCollectedThisMonth(bankCollected)
                .cashCollectedThisMonth(cashCollected)
                // Legacy field: month-only estimate (kept for any callers that use it)
                .totalPendingAmount(annualPendingDues)
                // New annual fields
                .annualPendingDues(annualPendingDues)
                .totalExpectedYTD(totalExpectedYTD)
                .totalCollectedYTD(totalCollectedYTD)
                .currentMonth(currentMonthStr)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/admin/payments/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TransactionLedgerEntryDTO>>> getTransactionLedger() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getTransactionLedger()));
    }

    @PutMapping("/admin/payments/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePayment(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success("Payment approved", paymentService.approvePayment(id, auth.getName())));
    }

    @PutMapping("/admin/payments/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> rejectPayment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(
                ApiResponse.success("Payment rejected", paymentService.rejectPayment(id, reason)));
    }

    @DeleteMapping("/admin/payments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            @PathVariable Long id, Authentication auth) {
        paymentService.deletePayment(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Payment record deleted", null));
    }

    @PostMapping("/user/maintenance/pay")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> submitPayment(
            @RequestBody PaymentRequest req, Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        PaymentResponseDTO p = paymentService.submitForVerification(owner.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Payment submitted for admin verification", p));
    }

    @GetMapping("/user/payments/history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PaymentResponseDTO>> getMyPayments(Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        // Family Members see all payments for their linked flat/property
        return ResponseEntity.ok(paymentService.getResidentPayments(owner.getId()));
    }
}