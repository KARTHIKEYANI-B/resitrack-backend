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

    /**
     * POST /admin/payments — Admin Manual Payment Registration.
     *
     * Lets an Admin/Super Admin record a monthly maintenance payment on an
     * owner's behalf (cash collected in person, a bank transfer confirmed
     * outside the app, etc.). Matches the frontend's
     * adminAPI.createAdminPayment(data) call in PaymentTracking.jsx, which
     * was previously POSTing to this exact path with no matching mapping —
     * hence "Request method 'POST' is not supported".
     */
    @PostMapping("/admin/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createAdminPayment(
            @RequestBody AdminPaymentRequest req) {
        PaymentResponseDTO p = paymentService.registerAdminPayment(req);
        String message = Boolean.TRUE.equals(req.getVerifiedByAdmin())
                ? "Payment recorded and verified"
                : "Payment recorded, pending verification";
        return ResponseEntity.ok(ApiResponse.success(message, p));
    }

    /**
     * GET /admin/payments/tracking-stats
     *
     * Task 1 — corrected Pending Dues formula:
     *
     *   annualPendingDues = totalExpectedYTD − totalCollectedYTD
     *
     * where:
     *   totalExpectedYTD  = activeOwners × maintAmountPerOwner × completedMonths
     *                       (Jan … previous calendar month, inclusive)
     *   totalCollectedYTD = SUM of all PAID payments Jan … previousMonth of current year
     *
     * "Completed months" = months from January up to (but NOT including) the current
     *  calendar month, because the current month is still in progress.
     *  Example: if today is June, completedMonths = 5 (Jan–May).
     *
     * This replaces the old formula: unpaidOwners × flatMaintAmount  (wrong because
     * it was month-only, not year-based, and did not subtract actual collections).
     *
     * The existing fields (paidOwners, unpaidOwners, totalCollectedThisMonth, etc.)
     * are unchanged — only totalPendingAmount and the three new annual fields change.
     */
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

        // ── Current-month collection ──────────────────────────────────────
        Double collected = paymentRepo.sumPaidAmountByYearAndMonth(year, month);
        collected = collected == null ? 0.0 : collected;

        Double bankCollected = paymentRepo.sumBankCollectedByYearAndMonth(year, month);
        Double cashCollected = paymentRepo.sumCashCollectedByYearAndMonth(year, month);
        bankCollected = bankCollected == null ? 0.0 : bankCollected;
        cashCollected = cashCollected == null ? 0.0 : cashCollected;

        double collPct = activeOwners > 0 ? (paidOwners * 100.0 / activeOwners) : 0.0;

        // ── Task 1: Annual Pending Dues ───────────────────────────────────
        //
        // Formula:
        //   annualPendingDues = totalExpectedYTD − totalCollectedYTD
        //
        // completedMonths: January up to (not including) the current month.
        //   • If month = 1 (January), no month has completed yet → completedMonths = 0.
        //   • If month = 6 (June), completedMonths = 5 (Jan–May).
        //
        // maintAmountPerOwner: from active Maintenance config.
        //   Uses ratePerSqFt if set (average approach: flat amount × owners is the
        //   conservative fallback since per-owner sqFt varies; the admin can cross-check
        //   with Maintenance List for exact per-owner breakdown).
        //
        // totalCollectedYTD: sum of all PAID payments from Jan to previousMonth,
        //   fetched directly from DB via sumCollectedByYearUpToMonth().

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

    /**
     * GET /admin/payments/transactions
     *
     * Admin → Payment Management unified transaction ledger: owner monthly
     * maintenance payments (PAID) + maintenance batch payments (PAID) +
     * expense records, combined into one list sorted by transaction date
     * descending (latest first), with a sequential serialNo. Purely additive
     * — reads existing data only, does not affect tracking-stats, approve/
     * reject, Payment Verification, Maintenance Summary, Financial Summary,
     * or the Dashboard.
     */
    @GetMapping("/admin/payments/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TransactionLedgerEntryDTO>>> getTransactionLedger() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getTransactionLedger()));
    }

    @PutMapping("/admin/payments/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePayment(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Payment approved", paymentService.approvePayment(id)));
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

    @PostMapping("/user/maintenance/pay")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> submitPayment(
            @RequestBody PaymentRequest req, Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        // Route FM -> owner: maintenance belongs to the property (owner row).
        // Storing under owner.getId() ensures pending-dues, dashboard, and
        // maintenance-list queries (all keyed on owner resident_id) update
        // automatically. For OWNER accounts this returns r unchanged.
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