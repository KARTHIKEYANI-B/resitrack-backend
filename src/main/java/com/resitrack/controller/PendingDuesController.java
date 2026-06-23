package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Payment;
import com.resitrack.entity.Resident;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.service.MaintenanceService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pending-dues endpoints — Admin and User.
 *
 * SOURCE OF TRUTH: Maintenance List (per-owner iteration using findAllActiveNonDeleted).
 *
 * PROPERTY-LEVEL PAYMENT AGGREGATION (root-cause fix):
 *  All paid-sum lookups use sumPaidAmountByPropertyAndPaymentMonth(owner.id, month)
 *  which aggregates PAID payments for the owner AND every Family Member whose
 *  ownerResidentId = owner.id.  This ensures FM payments are always counted.
 *
 * STATUS RULES (same as Maintenance List):
 *  pendingAmount == 0                            → PAID → excluded from Pending Dues
 *  paidSoFar > 0 AND paidSoFar < ownerMaintAmount → UNPAID (PARTIAL removed)
 *  paidSoFar == 0                                 → PENDING (UNPAID)
 */
@RestController
@RequiredArgsConstructor
public class PendingDuesController {

    private final PaymentRepository     paymentRepo;
    private final ResidentRepository    residentRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final MaintenanceService    maintenanceService;
    private final ResidentService       residentService;

    // ── Admin: all OWNER residents with outstanding dues ──────────────────
    @GetMapping("/admin/pending-dues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAdminPendingDues() {

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        LocalDate defaultDueDate = activeMaint.map(Maintenance::getDueDate).orElse(LocalDate.now());
        double    defaultLateFee = activeMaint
                .filter(m -> Boolean.TRUE.equals(m.getLateFeeEnabled()))
                .map(m -> m.getLateFee() != null ? m.getLateFee().doubleValue() : 0.0)
                .orElse(0.0);

        String currentMonth = LocalDate.now().getYear() + "-"
                + String.format("%02d", LocalDate.now().getMonthValue());

        // Same owner set as Maintenance List (OWNER rows only)
        List<Resident> activeOwners = residentRepo.findAllActiveNonDeleted();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Resident r : activeOwners) {
            // Per-owner maintenance amount — same formula as Maintenance List
            BigDecimal calcAmountBD = activeMaint
                    .map(m -> maintenanceService.calculateAmountForResident(m, r.getSqFt()))
                    .orElse(BigDecimal.ZERO);
            double ownerMaintAmount = calcAmountBD.doubleValue();

            // PROPERTY-LEVEL paid sum: owner + all linked FM accounts
            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                    r.getId(), currentMonth);
            double paidSoFar = paidRaw == null ? 0.0 : paidRaw;

            // pendingAmount — authoritative value, same formula as Maintenance List
            double pendingAmount = BigDecimal.valueOf(ownerMaintAmount)
                    .subtract(BigDecimal.valueOf(paidSoFar))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            // pendingAmount == 0 → PAID → remove completely from Pending Dues
            // Use a small tolerance (< 0.005 = less than half a paisa) to
            // avoid floating-point imprecision causing a paid owner to appear here.
            if (pendingAmount < 0.005) continue;

            // Two-state model: any owner with pendingAmount > 0 is UNPAID (PENDING).
            // PARTIAL is no longer a valid status.
            String status = "PENDING";

            // Late fee: only if past due date and no payment at all
            double lateFee = 0.0;
            if (defaultLateFee > 0 && defaultDueDate != null
                    && LocalDate.now().isAfter(defaultDueDate)
                    && paidSoFar == 0.0) {
                lateFee = defaultLateFee;
            }

            // Look up an existing PENDING/OVERDUE payment record id for penalty/notify actions
            Long paymentId = paymentRepo.findByResidentIdAndPaymentMonth(r.getId(), currentMonth)
                    .stream()
                    .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.PENDING
                              || p.getPaymentStatus() == Payment.PaymentStatus.OVERDUE)
                    .map(Payment::getId)
                    .findFirst()
                    .orElse(null);

            result.add(buildEntry(
                    paymentId, r, currentMonth, defaultDueDate,
                    ownerMaintAmount, lateFee, paidSoFar, pendingAmount, status));
        }

        // Order: paymentMonth ASC (oldest first), then resident name
        result.sort(Comparator
                .comparing((Map<String, Object> e) -> String.valueOf(e.get("paymentMonth")))
                .thenComparing(e -> String.valueOf(e.get("residentName"))));

        return ResponseEntity.ok(result);
    }

    /**
     * GET /admin/pending-dues/summary
     *
     * Returns accurate aggregate stats using property-level payment sums:
     *  - totalPendingAmount : Σ pendingAmount for owners where pendingAmount > 0
     *  - unpaidOwnerCount   : count of owners with pendingAmount > 0
     *  - overdueCount       : owners with pendingAmount > 0 AND past due date
     *  - partialCount       : owners with paidSoFar > 0 but < ownerMaintAmount
     *
     * Owners with pendingAmount == 0 are PAID; not counted in any pending stat.
     */
    @GetMapping("/admin/pending-dues/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminPendingDuesSummary() {

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        LocalDate dueDate    = activeMaint.map(Maintenance::getDueDate).orElse(null);

        String currentMonth = LocalDate.now().getYear() + "-"
                + String.format("%02d", LocalDate.now().getMonthValue());

        boolean pastDue = dueDate != null && LocalDate.now().isAfter(dueDate);

        List<Resident> activeOwners = residentRepo.findAllActiveNonDeleted();

        double totalPendingAmount = 0.0;
        int    overdueCount       = 0;
        int    unpaidCount        = 0;

        for (Resident r : activeOwners) {
            BigDecimal calcAmountBD = activeMaint
                    .map(m -> maintenanceService.calculateAmountForResident(m, r.getSqFt()))
                    .orElse(BigDecimal.ZERO);
            double ownerMaintAmount = calcAmountBD.doubleValue();

            // PROPERTY-LEVEL paid sum
            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                    r.getId(), currentMonth);
            double paidSoFar = paidRaw == null ? 0.0 : paidRaw;

            double pendingAmount = BigDecimal.valueOf(ownerMaintAmount)
                    .subtract(BigDecimal.valueOf(paidSoFar))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            // Two-state model: any owner with pendingAmount > 0 is UNPAID.
            // PARTIAL is removed — partial payments still count as UNPAID.
            if (pendingAmount > 0.0) {
                totalPendingAmount += pendingAmount;
                unpaidCount++;
                if (pastDue) {
                    overdueCount++;
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPendingAmount", Math.round(totalPendingAmount * 100.0) / 100.0);
        summary.put("overdueCount",       overdueCount);
        summary.put("unpaidCount",        unpaidCount);
        summary.put("totalActiveOwners",  activeOwners.size());
        summary.put("currentMonth",       currentMonth);
        summary.put("dueDate",            dueDate != null ? dueDate.toString() : null);

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @PostMapping("/admin/pending-dues/{id}/penalty")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyPenalty(@PathVariable Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + id));

        if (p.getPaymentStatus() == Payment.PaymentStatus.PAID)
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Cannot apply penalty to an already paid record"));

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        double lateFee = activeMaint
                .filter(m -> Boolean.TRUE.equals(m.getLateFeeEnabled()))
                .map(m -> m.getLateFee() != null ? m.getLateFee().doubleValue() : 0.0)
                .orElse(0.0);

        if (lateFee <= 0)
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No active late fee configured in maintenance settings"));

        p.setLateFeeAmount(BigDecimal.valueOf(lateFee));
        p.setPaymentStatus(Payment.PaymentStatus.OVERDUE);
        paymentRepo.save(p);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("paymentId",     p.getId());
        res.put("lateFeeApplied", lateFee);
        res.put("newStatus",     "OVERDUE");
        return ResponseEntity.ok(ApiResponse.success("Late fee penalty applied", res));
    }

    // ── User: own pending dues ─────────────────────────────────────────────
    @GetMapping("/user/pending-dues")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Map<String, Object>>> getUserPendingDues(Authentication auth) {
        Resident raw = residentService.getByEmail(auth.getName());
        // Route FM -> owner: pending dues belong to the property (owner row).
        Resident r   = residentService.getEffectiveOwnerResident(raw);

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        LocalDate defaultDueDate = activeMaint.map(Maintenance::getDueDate).orElse(LocalDate.now());
        double    defaultLateFee = activeMaint
                .filter(m -> Boolean.TRUE.equals(m.getLateFeeEnabled()))
                .map(m -> m.getLateFee() != null ? m.getLateFee().doubleValue() : 0.0)
                .orElse(0.0);

        // Per-owner maintenance amount using sq-ft formula
        double maintAmount = activeMaint
                .map(m -> maintenanceService.calculateAmountForResident(m, r.getSqFt()).doubleValue())
                .orElse(0.0);

        String currentMonth = LocalDate.now().getYear() + "-"
                + String.format("%02d", LocalDate.now().getMonthValue());

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> processedMonths = new HashSet<>();

        List<Payment> pendingPayments = paymentRepo.findPendingOrOverdueByResident(r.getId());
        for (Payment p : pendingPayments) {
            Map<String, Object> entry = buildDueEntry(p, defaultDueDate, defaultLateFee, maintAmount, r.getId());
            double remaining = (double) entry.get("remainingDue");
            if (remaining > 0) {
                result.add(entry);
                if (p.getPaymentMonth() != null) processedMonths.add(p.getPaymentMonth());
            }
        }

        if (!processedMonths.contains(currentMonth) && maintAmount > 0) {
            // Property-level paid sum for current month
            Double paidSoFarRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                    r.getId(), currentMonth);
            double paid = paidSoFarRaw == null ? 0.0 : paidSoFarRaw;

            double remainingDue = BigDecimal.valueOf(maintAmount)
                    .subtract(BigDecimal.valueOf(paid))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            // Only show if pendingAmount > 0 (not PAID)
            if (remainingDue > 0) {
                double lateFee = 0.0;
                if (defaultLateFee > 0 && defaultDueDate != null
                        && LocalDate.now().isAfter(defaultDueDate)) {
                    lateFee = defaultLateFee;
                }
                String status = "PENDING";  // two-state: PARTIAL removed
                result.add(0, buildEntry(
                        null, r, currentMonth, defaultDueDate,
                        maintAmount, lateFee, paid, remainingDue, status));
            }
        }

        return ResponseEntity.ok(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Map<String, Object> buildDueEntry(
            Payment p, LocalDate defaultDueDate, double defaultLateFee,
            double maintAmount, Long ownerResidentId) {

        Resident r = p.getResident();
        LocalDate dueDate = (p.getMaintenance() != null && p.getMaintenance().getDueDate() != null)
                ? p.getMaintenance().getDueDate()
                : defaultDueDate;

        double lateFee = p.getLateFeeAmount() != null ? p.getLateFeeAmount().doubleValue() : 0.0;

        double assignedAmount = maintAmount > 0 ? maintAmount
                : (p.getAmount() != null ? p.getAmount().doubleValue() : 0.0);

        String paymentMonth = p.getPaymentMonth();

        // Property-level paid sum (owner + FM) for the payment month
        double paidSoFar = 0.0;
        if (paymentMonth != null) {
            Double paid = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                    ownerResidentId, paymentMonth);
            paidSoFar = paid == null ? 0.0 : paid;
        }

        double remainingDue = BigDecimal.valueOf(assignedAmount)
                .subtract(BigDecimal.valueOf(paidSoFar))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        String status = p.getPaymentStatus().name();

        return buildEntry(p.getId(), r, paymentMonth, dueDate,
                assignedAmount, lateFee, paidSoFar, remainingDue, status);
    }

    private Map<String, Object> buildEntry(
            Long paymentId, Resident r, String paymentMonth, LocalDate dueDate,
            double dueAmount, double lateFee, double paidSoFar,
            double remainingDue, String status) {

        long daysOverdue = dueDate != null && LocalDate.now().isAfter(dueDate)
                ? ChronoUnit.DAYS.between(dueDate, LocalDate.now()) : 0;

        String monthLabel = paymentMonth;
        if (monthLabel != null && monthLabel.matches("\\d{4}-\\d{2}")) {
            try {
                String[] parts = monthLabel.split("-");
                int yr = Integer.parseInt(parts[0]);
                int mo = Integer.parseInt(parts[1]);
                String[] MONTHS = {"","January","February","March","April","May","June",
                                   "July","August","September","October","November","December"};
                monthLabel = MONTHS[mo] + " " + yr;
            } catch (Exception ignored) {}
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id",             paymentId);
        entry.put("residentId",     r != null ? r.getId()         : null);
        entry.put("residentName",   r != null ? r.getFullName()   : "—");
        entry.put("flatNumber",     r != null ? r.getFlatNumber() : "—");
        entry.put("flatType",       r != null ? r.getFlatType()   : "—");
        entry.put("propertyType",   r != null && r.getPropertyType() != null
                                        ? r.getPropertyType().name() : "FLAT");
        entry.put("sqFt",           r != null ? r.getSqFt()       : null);
        entry.put("month",          monthLabel);
        entry.put("paymentMonth",   paymentMonth);
        entry.put("dueDate",        dueDate != null ? dueDate.toString() : null);
        entry.put("assignedAmount", dueAmount);
        entry.put("dueAmount",      dueAmount);
        entry.put("lateFee",        lateFee);
        entry.put("totalDue",       dueAmount + lateFee);
        entry.put("paidSoFar",      paidSoFar);
        entry.put("paidAmount",     paidSoFar);
        entry.put("remainingDue",   remainingDue);
        entry.put("pendingAmount",  remainingDue);
        entry.put("status",         status);
        entry.put("daysOverdue",    daysOverdue);
        return entry;
    }
}