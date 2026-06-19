package com.resitrack.service;

import com.resitrack.dto.MaintenanceBatchRequest;
import com.resitrack.dto.MaintenanceListDTO;
import com.resitrack.dto.MaintenanceOwnerDTO;
import com.resitrack.dto.MaintenanceRequest;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository      maintenanceRepo;
    private final MaintenanceBatchRepository batchRepo;
    private final PaymentRepository          paymentRepo;
    private final ResidentRepository         residentRepo;

    // ── Basic CRUD ────────────────────────────────────────────────────────

    public List<Maintenance> getAll() {
        return maintenanceRepo.findAll();
    }

    public Maintenance create(MaintenanceRequest req) {
        BigDecimal amount = resolveAmount(req);
        Maintenance m = Maintenance.builder()
                .maintenanceType(req.getMaintenanceType() != null ? req.getMaintenanceType() : "Monthly")
                .ratePerSqFt(req.getRatePerSqFt())
                .amount(amount)
                .dueDate(req.getDueDate())
                .lateFee(req.getLateFee() != null ? BigDecimal.valueOf(req.getLateFee()) : BigDecimal.ZERO)
                .lateFeeEnabled(req.isLateFeeEnabled())
                .active(true)          // always set explicitly to prevent null
                .build();
        return maintenanceRepo.save(m);
    }

    public Maintenance update(Long id, MaintenanceRequest req) {
        Maintenance m = maintenanceRepo.findById(id)
                .orElseThrow(() -> new CustomException("Maintenance not found", HttpStatus.NOT_FOUND));
        m.setMaintenanceType(req.getMaintenanceType());
        m.setRatePerSqFt(req.getRatePerSqFt());
        m.setAmount(resolveAmount(req));
        m.setDueDate(req.getDueDate());
        if (req.getLateFee() != null) m.setLateFee(BigDecimal.valueOf(req.getLateFee()));
        m.setLateFeeEnabled(req.isLateFeeEnabled());
        return maintenanceRepo.save(m);
    }

    public void delete(Long id) {
        if (!maintenanceRepo.existsById(id))
            throw new CustomException("Maintenance not found", HttpStatus.NOT_FOUND);
        maintenanceRepo.deleteById(id);
    }

    // ── Core calculation: owner's maintenance amount ─────────────────────
    //
    // Formula: maintenanceAmount = ceil(owner.sqFt × ratePerSqFt)
    // Fallback: use flat `amount` when ratePerSqFt is null or sqFt is missing.
    //
    // ROUNDING RULE: always round UP to the next whole rupee (CEILING).
    //
    //   Example: 723 sq.ft × ₹2.70/sq.ft = 1952.1  →  ₹1953
    //   Example: 1000 sq.ft × ₹2.70/sq.ft = 2700.0  →  ₹2700  (whole number unchanged)
    //
    // Why CEILING instead of HALF_UP:
    //   HALF_UP rounds 1952.1 → 1952, so a resident who pays ₹1952 (the
    //   displayed amount) leaves a ₹0.10 pending balance and stays UNPAID.
    //   CEILING always rounds fractional paise UP to the next whole rupee,
    //   so the displayed amount equals the required amount exactly.

    public BigDecimal calculateAmountForResident(Maintenance m, Double sqFt) {
        if (m.getRatePerSqFt() != null
                && m.getRatePerSqFt().compareTo(BigDecimal.ZERO) > 0
                && sqFt != null && sqFt > 0) {
            return m.getRatePerSqFt()
                    .multiply(BigDecimal.valueOf(sqFt))
                    .setScale(0, RoundingMode.CEILING);   // ceil to whole rupee
        }
        return m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
    }

    public BigDecimal calculateAmountForResident(Maintenance m, Integer squareFeet) {
        return calculateAmountForResident(m, squareFeet != null ? squareFeet.doubleValue() : null);
    }

    public Maintenance getActiveMonthlyMaintenance() {
        return maintenanceRepo.findFirstByMaintenanceTypeAndActiveTrue("Monthly")
                .orElseThrow(() -> new CustomException(
                        "No active monthly maintenance configured", HttpStatus.NOT_FOUND));
    }

    // ── Active maintenance config lookup (no type filter) ──────────────────
    // Same query used by getOwnerMaintenanceList() — the authoritative source
    // for "which Maintenance row is currently active" used by the Maintenance
    // Summary screen. Exposed here so other services (e.g. financial reports)
    // can reuse the exact same active-maintenance row + calculateAmountForResident()
    // formula instead of maintaining a separate, possibly-stale calculation.
    public Optional<Maintenance> getActiveMaintenanceConfig() {
        return maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
    }

    // ── Maintenance List ──────────────────────────────────────────────────
    //
    // GET /admin/maintenance/owner-list?year=YYYY&month=MM
    //
    // Returns per-owner maintenance status for the given month.
    //
    // STATUS RULES (authoritative — same rules used by Dashboard and Pending Dues):
    //
    //   paidAmount  = sumPaidAmountByPropertyAndPaymentMonth(owner.id, month)
    //               = owner's own PAID payments + ALL linked FM PAID payments
    //
    //   pendingAmount = max(0, maintenanceAmount - paidAmount)
    //
    //   pendingAmount == 0               → PAID     (even if FM made the last payment)
    //   pendingAmount >  0 AND paid > 0  → PARTIAL
    //   paidAmount == 0                  → UNPAID

    public MaintenanceListDTO getOwnerMaintenanceList(int year, int month) {
        String paymentMonth = String.format("%d-%02d", year, month);
        String monthLabel   = buildMonthLabel(year, month);

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);
        BigDecimal ratePerSqFt = activeMaint.map(Maintenance::getRatePerSqFt).orElse(null);
        BigDecimal flatAmount  = activeMaint.map(Maintenance::getAmount).orElse(BigDecimal.ZERO);

        List<Resident> flatOwners  = residentRepo.findActiveNonDeletedByPropertyType(PropertyType.FLAT);
        List<Resident> villaOwners = residentRepo.findActiveNonDeletedByPropertyType(PropertyType.VILLA);

        List<MaintenanceOwnerDTO> flatDTOs  = buildOwnerDTOs(flatOwners,  activeMaint.orElse(null), paymentMonth);
        List<MaintenanceOwnerDTO> villaDTOs = buildOwnerDTOs(villaOwners, activeMaint.orElse(null), paymentMonth);

        BigDecimal totalFlat  = flatDTOs.stream()
                .map(MaintenanceOwnerDTO::getMaintenanceAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVilla = villaDTOs.stream()
                .map(MaintenanceOwnerDTO::getMaintenanceAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // paidCount: any owner whose pendingAmount == 0 (includes FM-completed payments)
        long paidFlat  = flatDTOs.stream().filter(d -> "PAID".equals(d.getPaymentStatus())).count();
        long paidVilla = villaDTOs.stream().filter(d -> "PAID".equals(d.getPaymentStatus())).count();

        return MaintenanceListDTO.builder()
                .paymentMonth(paymentMonth)
                .monthLabel(monthLabel)
                .ratePerSqFt(ratePerSqFt)
                .flatAmount(flatAmount)
                .flatOwners(flatDTOs)
                .villaOwners(villaDTOs)
                .totalFlatMaintenance(totalFlat)
                .totalVillaMaintenance(totalVilla)
                .grandTotal(totalFlat.add(totalVilla))
                .totalFlatOwners(flatDTOs.size())
                .totalVillaOwners(villaDTOs.size())
                .paidFlatOwners((int) paidFlat)
                .paidVillaOwners((int) paidVilla)
                .build();
    }

    /**
     * Builds per-owner DTOs for the Maintenance List.
     *
     * ═══════════════════════════════════════════════════════════════════════
     * FAMILY MEMBER PAYMENT SYNCHRONIZATION
     * ═══════════════════════════════════════════════════════════════════════
     *
     * sumPaidAmountByPropertyAndPaymentMonth(owner.id, month) aggregates:
     *   1. Payments where p.resident.id == owner.id          (owner's own payments)
     *   2. Payments where p.resident.ownerResidentId == owner.id  (FM payments)
     *
     * This means that when a Family Member makes a payment — even the FINAL
     * payment that brings the balance to zero — it is counted at the property
     * level.  pendingAmount becomes 0 and status becomes PAID automatically.
     *
     * ═══════════════════════════════════════════════════════════════════════
     * STATUS DETERMINATION (authoritative)
     * ═══════════════════════════════════════════════════════════════════════
     *
     *   pendingAmount = max(0, maintenanceAmount - paidAmount)
     *
     *   pendingAmount == 0               → "PAID"
     *   pendingAmount >  0 AND paid > 0  → "PARTIAL"
     *   paidAmount == 0                  → "UNPAID"
     *
     * The status label is the SOLE source of truth.  Callers must not use
     * any other heuristic to determine payment status.
     */
    private List<MaintenanceOwnerDTO> buildOwnerDTOs(
            List<Resident> owners, Maintenance maint, String paymentMonth) {

        return owners.stream().map(r -> {
            BigDecimal calcAmount = maint != null
                    ? calculateAmountForResident(maint, r.getSqFt())
                    : BigDecimal.ZERO;

            // ── Property-level paid sum: owner + ALL linked FM payments ───
            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                    r.getId(), paymentMonth);
            BigDecimal paidAmount = paidRaw != null
                    ? BigDecimal.valueOf(paidRaw).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // ── Pending = max(0, maintenance - paid) ─────────────────────
            BigDecimal pending = calcAmount.subtract(paidAmount).max(BigDecimal.ZERO);

            // ── Status determination — ONLY from pendingAmount ────────────
            //
            // Rule 1: pendingAmount == 0  → PAID
            //         (covers both fully paid and zero-amount cases)
            //         This handles the case where the FINAL payment was made
            //         by a Family Member.
            //
            // Rule 2: pendingAmount >  0 AND paidAmount > 0  → PARTIAL
            //
            // Rule 3: paidAmount == 0  → UNPAID
            //         (no payments at all this month)
            final String status;
            if (pending.compareTo(BigDecimal.ZERO) == 0) {
                status = "PAID";
            } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = "PARTIAL";
            } else {
                status = "UNPAID";
            }

            return MaintenanceOwnerDTO.builder()
                    .residentId(r.getId())
                    .fullName(r.getFullName())
                    .flatNumber(r.getFlatNumber())
                    .flatType(r.getFlatType())
                    .propertyType(r.getPropertyType() != null ? r.getPropertyType().name() : "FLAT")
                    .sqFt(r.getSqFt())
                    .ratePerSqFt(maint != null ? maint.getRatePerSqFt() : null)
                    .maintenanceAmount(calcAmount)
                    .paymentStatus(status)
                    .paidAmount(paidAmount)
                    .pendingAmount(pending)
                    .paymentMonth(paymentMonth)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Batch management ──────────────────────────────────────────────────

    public List<Map<String, Object>> getAllBatches() {
        List<MaintenanceBatch> batches = batchRepo.findAllByOrderByCreatedAtDesc();
        return batches.stream()
                .map(this::enrichBatch)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBatchById(Long id) {
        MaintenanceBatch batch = batchRepo.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        return enrichBatch(batch);
    }

    @Transactional
    public Map<String, Object> createBatch(MaintenanceBatchRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank())
            throw new CustomException("Batch title is required", HttpStatus.BAD_REQUEST);
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Amount must be positive", HttpStatus.BAD_REQUEST);
        if (req.getDueDate() == null)
            throw new CustomException("Due date is required", HttpStatus.BAD_REQUEST);

        List<Resident> residents = findMatchingResidents(req);
        if (residents.isEmpty())
            throw new CustomException("No active residents match the assignment criteria", HttpStatus.BAD_REQUEST);

        String paymentMonth = String.format("%d-%02d",
                req.getDueDate().getYear(),
                req.getDueDate().getMonthValue());
        String paymentYear = String.valueOf(req.getDueDate().getYear());

        String assignedFlatsDisplay = buildAssignedFlatsDisplay(req, residents);
        MaintenanceBatch batch = MaintenanceBatch.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory() != null ? req.getCategory() : "Monthly Maintenance")
                .amount(req.getAmount())
                .dueDate(req.getDueDate())
                .penaltyAmount(req.isPenaltyEnabled() && req.getPenaltyAmount() != null
                        ? req.getPenaltyAmount() : BigDecimal.ZERO)
                .penaltyEnabled(req.isPenaltyEnabled())
                .assignmentType(MaintenanceBatch.AssignmentType.valueOf(
                        req.getAssignmentType() != null ? req.getAssignmentType() : "ALL"))
                .assignedFlats(assignedFlatsDisplay)
                .status(MaintenanceBatch.BatchStatus.ACTIVE)
                .totalAssigned(0)
                .build();
        batch = batchRepo.save(batch);

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);

        int assigned = 0;
        String batchDesc = "BATCH-" + batch.getId() + ": " + req.getTitle();
        for (Resident r : residents) {
            if (paymentRepo.existsByResidentIdAndPaymentMonth(r.getId(), paymentMonth)) continue;

            BigDecimal residentAmount = activeMaint
                    .map(m -> calculateAmountForResident(m, r.getSqFt()))
                    .orElse(req.getAmount());

            BigDecimal lateFee = req.isPenaltyEnabled() && req.getPenaltyAmount() != null
                    ? req.getPenaltyAmount() : BigDecimal.ZERO;

            Payment payment = Payment.builder()
                    .resident(r)
                    .amount(residentAmount)
                    .lateFeeAmount(lateFee)
                    .paymentStatus(Payment.PaymentStatus.PENDING)
                    .paymentMonth(paymentMonth)
                    .paymentYear(paymentYear)
                    .description(batchDesc)
                    .build();
            paymentRepo.save(payment);
            assigned++;
        }

        batch.setTotalAssigned(assigned);
        batchRepo.save(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId",       batch.getId());
        result.put("totalAssigned", assigned);
        result.put("paymentMonth",  paymentMonth);
        result.put("title",         batch.getTitle());
        return result;
    }

    public MaintenanceBatch updateBatchStatus(Long id, String status) {
        MaintenanceBatch batch = batchRepo.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        batch.setStatus(MaintenanceBatch.BatchStatus.valueOf(status.toUpperCase()));
        return batchRepo.save(batch);
    }

    public void deleteBatch(Long id) {
        if (!batchRepo.existsById(id))
            throw new CustomException("Batch not found", HttpStatus.NOT_FOUND);
        batchRepo.deleteById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private List<Resident> findMatchingResidents(MaintenanceBatchRequest req) {
        List<Resident> all = residentRepo
                .findByIsApprovedTrueAndStatus(Resident.ResidentStatus.ACTIVE);

        String type = req.getAssignmentType() != null ? req.getAssignmentType() : "ALL";

        switch (type.toUpperCase()) {
            case "BLOCK":
                String prefix = req.getBlockPrefix();
                if (prefix == null || prefix.isBlank())
                    throw new CustomException("Block prefix is required for BLOCK assignment", HttpStatus.BAD_REQUEST);
                return all.stream()
                        .filter(r -> r.getFlatNumber() != null
                                && r.getFlatNumber().toUpperCase().startsWith(prefix.toUpperCase()))
                        .collect(Collectors.toList());

            case "VILLA_GROUP":
                return all.stream()
                        .filter(r -> r.getPropertyType() == PropertyType.VILLA)
                        .collect(Collectors.toList());

            case "FLAT_GROUP":
                return all.stream()
                        .filter(r -> r.getPropertyType() == PropertyType.FLAT)
                        .collect(Collectors.toList());

            case "INDIVIDUAL":
                List<String> selected = req.getSelectedFlats();
                if (selected == null || selected.isEmpty())
                    throw new CustomException("At least one flat must be selected", HttpStatus.BAD_REQUEST);
                return all.stream()
                        .filter(r -> selected.contains(r.getFlatNumber()))
                        .collect(Collectors.toList());

            default: // ALL
                return all;
        }
    }

    private String buildAssignedFlatsDisplay(MaintenanceBatchRequest req, List<Resident> residents) {
        String type = req.getAssignmentType() != null ? req.getAssignmentType() : "ALL";
        switch (type.toUpperCase()) {
            case "BLOCK":       return req.getBlockPrefix() + "-Block (" + residents.size() + " flats)";
            case "VILLA_GROUP": return "Villa Group (" + residents.size() + " units)";
            case "FLAT_GROUP":  return "Flat Group (" + residents.size() + " units)";
            case "INDIVIDUAL":
                return req.getSelectedFlats() != null
                        ? String.join(",", req.getSelectedFlats())
                        : "Custom";
            default:            return "ALL";
        }
    }

    private Map<String, Object> enrichBatch(MaintenanceBatch batch) {
        String paymentMonth = String.format("%d-%02d",
                batch.getDueDate().getYear(),
                batch.getDueDate().getMonthValue());

        long paid    = paymentRepo.countPaidByPaymentMonth(paymentMonth);
        long pending = paymentRepo.countPendingByPaymentMonth(paymentMonth);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",             batch.getId());
        map.put("title",          batch.getTitle());
        map.put("description",    batch.getDescription());
        map.put("category",       batch.getCategory());
        map.put("amount",         batch.getAmount());
        map.put("dueDate",        batch.getDueDate());
        map.put("penaltyAmount",  batch.getPenaltyAmount());
        map.put("penaltyEnabled", batch.isPenaltyEnabled());
        map.put("assignmentType", batch.getAssignmentType());
        map.put("assignedFlats",  batch.getAssignedFlats());
        map.put("status",         batch.getStatus());
        map.put("totalAssigned",  batch.getTotalAssigned());
        map.put("totalPaid",      (int) paid);
        map.put("totalPending",   (int) pending);
        map.put("paymentMonth",   paymentMonth);
        map.put("createdAt",      batch.getCreatedAt());
        return map;
    }

    private BigDecimal resolveAmount(MaintenanceRequest req) {
        if (req.getAmount() != null && req.getAmount() > 0)
            return BigDecimal.valueOf(req.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (req.getRatePerSqFt() != null)
            return req.getRatePerSqFt().setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
    }

    private String buildMonthLabel(int year, int month) {
        return LocalDate.of(year, month, 1)
                .getMonth()
                .getDisplayName(TextStyle.FULL, java.util.Locale.ENGLISH)
                + " " + year;
    }
}