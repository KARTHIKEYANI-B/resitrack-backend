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
    private final BatchPaymentRepository     batchPaymentRepo;

    // ── Basic CRUD ────────────────────────────────────────────────────────

    public List<Maintenance> getAll() {
        return maintenanceRepo.findAll();
    }

    @Transactional
    public Maintenance create(MaintenanceRequest req) {
        BigDecimal amount = resolveAmount(req);
        Maintenance m = Maintenance.builder()
                .maintenanceType(req.getMaintenanceType() != null ? req.getMaintenanceType() : "Monthly")
                .propertyType(req.getPropertyType())
                .ratePerSqFt(req.getRatePerSqFt())
                .amount(amount)
                .dueDate(req.getDueDate())
                .lateFee(req.getLateFee() != null ? BigDecimal.valueOf(req.getLateFee()) : BigDecimal.ZERO)
                .lateFeeEnabled(req.isLateFeeEnabled())
                .active(true)          // always set explicitly to prevent null
                .build();
        Maintenance saved = maintenanceRepo.save(m);

        // ── FIX: only ONE active row per property type at a time ───────────
        // Root cause of "Villa rate showing for Flat owners": creating a new
        // rate config never deactivated any previously-active row of the
        // SAME property type. If two active rows ever shared the same
        // propertyType (e.g. the admin left the Property Type dropdown on
        // its default value while entering the other property's rate),
        // "most recently created wins" was an undocumented, fragile
        // assumption with no guardrail. Deactivating siblings of the SAME
        // property type makes "which row is active" unambiguous, and never
        // touches the OTHER property type's active row.
        if (saved.getPropertyType() != null) {
            deactivateOtherActiveRowsOfSameType(saved);
        }
        return saved;
    }

    @Transactional
    public Maintenance update(Long id, MaintenanceRequest req) {
        Maintenance m = maintenanceRepo.findById(id)
                .orElseThrow(() -> new CustomException("Maintenance not found", HttpStatus.NOT_FOUND));
        m.setMaintenanceType(req.getMaintenanceType());
        m.setPropertyType(req.getPropertyType());
        m.setRatePerSqFt(req.getRatePerSqFt());
        m.setAmount(resolveAmount(req));
        m.setDueDate(req.getDueDate());
        if (req.getLateFee() != null) m.setLateFee(BigDecimal.valueOf(req.getLateFee()));
        m.setLateFeeEnabled(req.isLateFeeEnabled());
        Maintenance saved = maintenanceRepo.save(m);

        if (Boolean.TRUE.equals(saved.getActive()) && saved.getPropertyType() != null) {
            deactivateOtherActiveRowsOfSameType(saved);
        }
        return saved;
    }

    // Deactivates every OTHER active row that shares this row's propertyType
    // (never the other property type), so exactly one active row exists per
    // property type going forward. Idempotent — safe to call repeatedly.
    private void deactivateOtherActiveRowsOfSameType(Maintenance keep) {
        List<Maintenance> sameType =
                maintenanceRepo.findActiveByPropertyTypeOrderByCreatedAtDesc(keep.getPropertyType());
        for (Maintenance other : sameType) {
            if (!other.getId().equals(keep.getId())) {
                other.setActive(false);
                maintenanceRepo.save(other);
            }
        }
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

    // ── Flat/Villa separate rates ────────────────────────────────────────
    //
    // Resolves the active Maintenance row to use for a given property type:
    //   1. Prefer an active row whose propertyType exactly matches (a FLAT
    //      owner gets the FLAT-rate row, a VILLA owner gets the VILLA-rate
    //      row — "apply it only to FLAT/VILLA owners").
    //   2. Fall back to the legacy property-agnostic active row (propertyType
    //      IS NULL) for backward compatibility, so residents continue to be
    //      billed correctly even before an admin has configured separate
    //      Flat/Villa rates.
    //
    // This is the single chokepoint every consumer (Maintenance Summary,
    // Financial Summary, Owner/Family Member Dashboard, Monthly Maintenance
    // Bill) should call instead of getActiveMaintenanceConfig() once a
    // resident's property type is known.
    @Transactional
    public Optional<Maintenance> getActiveMaintenanceConfig(PropertyType propertyType) {
        if (propertyType != null) {
            List<Maintenance> matches =
                    maintenanceRepo.findActiveByPropertyTypeOrderByCreatedAtDesc(propertyType);
            if (!matches.isEmpty()) {
                Maintenance picked = matches.get(0);

                // ── Self-heal on read ────────────────────────────────────────
                // If more than one row is currently active for this property
                // type (e.g. leftover bad data from before the create()/
                // update() guardrail existed, or from any other path that
                // bypassed it), deactivate every row except the newest right
                // now — don't wait for the admin to save a config again
                // before the correct single-active-row state takes effect.
                // This makes the fix visible immediately on the very next
                // Maintenance Summary / Dashboard / Financial Summary read,
                // not just on the next write.
                if (matches.size() > 1) {
                    for (int i = 1; i < matches.size(); i++) {
                        Maintenance stale = matches.get(i);
                        stale.setActive(false);
                        maintenanceRepo.save(stale);
                    }
                }

                // Defensive sanity check: the query already filters on
                // m.propertyType = :propertyType, but this assertion makes
                // it structurally impossible for a mismatched row to ever
                // silently flow through — fail loudly instead of billing a
                // resident with the wrong property type's rate.
                if (picked.getPropertyType() != propertyType) {
                    throw new IllegalStateException(
                            "Maintenance rate lookup returned propertyType=" + picked.getPropertyType()
                            + " for requested propertyType=" + propertyType + " (id=" + picked.getId() + ")");
                }
                return Optional.of(picked);
            }
        }
        // Legacy fallback: the property-agnostic shared row, if any.
        return maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true)
                .filter(m -> m.getPropertyType() == null);
    }

    /** Convenience overload — resolves the active config directly from a Resident. */
    public Optional<Maintenance> getActiveMaintenanceConfigFor(Resident r) {
        return getActiveMaintenanceConfig(r != null ? r.getPropertyType() : null);
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

        // ── FIX: separate active rate config per property type ──────────────
        // Previously a single activeMaint row (no property-type filter) was
        // used for BOTH flat and villa owners. Now each property type
        // resolves its own active row (falling back to the legacy shared
        // row if no property-specific one is configured yet), so "Apply it
        // only to FLAT owners" / "Apply it only to VILLA owners" holds even
        // when the two rates differ.
        Optional<Maintenance> activeFlatMaint  = getActiveMaintenanceConfig(PropertyType.FLAT);
        Optional<Maintenance> activeVillaMaint = getActiveMaintenanceConfig(PropertyType.VILLA);

        BigDecimal flatRatePerSqFt  = activeFlatMaint.map(Maintenance::getRatePerSqFt).orElse(null);
        BigDecimal villaRatePerSqFt = activeVillaMaint.map(Maintenance::getRatePerSqFt).orElse(null);
        BigDecimal flatAmount  = activeFlatMaint.map(Maintenance::getAmount).orElse(BigDecimal.ZERO);

        List<Resident> flatOwners  = residentRepo.findActiveNonDeletedByPropertyType(PropertyType.FLAT);
        List<Resident> villaOwners = residentRepo.findActiveNonDeletedByPropertyType(PropertyType.VILLA);

        List<MaintenanceOwnerDTO> flatDTOs  = buildOwnerDTOs(flatOwners,  activeFlatMaint.orElse(null),  paymentMonth);
        List<MaintenanceOwnerDTO> villaDTOs = buildOwnerDTOs(villaOwners, activeVillaMaint.orElse(null), paymentMonth);

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
                .ratePerSqFt(flatRatePerSqFt)          // kept for backward compatibility (= flat rate)
                .flatRatePerSqFt(flatRatePerSqFt)
                .villaRatePerSqFt(villaRatePerSqFt)
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

        // ── FIX: Maintenance Batch assignment is now fully independent of
        // regular monthly maintenance.
        //
        // The old code below this point used to loop over matched residents
        // and write rows into the shared monthly `payments` table, SKIPPING
        // any resident who already had a payment row for that calendar
        // month (paymentRepo.existsByResidentIdAndPaymentMonth(...)). That
        // meant a resident who had already PAID their monthly maintenance
        // (and therefore already had a payments row for the month) was
        // silently excluded from the batch — e.g. with 27 total owners and
        // 7 already paid for the month, only the 20 unpaid owners got a
        // batch obligation, and `totalAssigned` reflected only those 20.
        //
        // Maintenance Batch assignment must never be filtered by, or write
        // into, the monthly maintenance ledger at all. Every resident
        // matched by findMatchingResidents() (ALL / BLOCK / VILLA_GROUP /
        // FLAT_GROUP / INDIVIDUAL) receives the batch obligation regardless
        // of their monthly maintenance payment status — that's what
        // createBatchPaymentRecords() below does, writing exclusively to
        // the independent `batch_payments` ledger.
        batch.setTotalAssigned(residents.size());
        batchRepo.save(batch);

        // ── Maintenance Batch Dues — the ONLY place batch obligations are
        // recorded. One BatchPayment row per resident matched above, using
        // the batch's OWN amount (req.getAmount()), with no dependency on
        // monthly maintenance payment status whatsoever. This is what
        // powers the resident-facing "Maintenance Batch Dues" section and
        // the admin "Paid List" / paid-unpaid counts.
        createBatchPaymentRecords(batch, residents, req.getAmount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId",       batch.getId());
        result.put("totalAssigned", residents.size());
        result.put("paymentMonth",  paymentMonth);
        result.put("title",         batch.getTitle());
        return result;
    }

    /**
     * Creates a BatchPayment (UNPAID) row for every resident matched by the
     * batch's assignment rule, then persists the batch's initial paid/unpaid
     * counts. Idempotent per (batch, resident) via the unique constraint on
     * batch_payments — safe even if called more than once for the same batch.
     */
    private void createBatchPaymentRecords(MaintenanceBatch batch, List<Resident> residents, BigDecimal amount) {
        BigDecimal dueAmount = amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? amount : batch.getAmount();

        for (Resident r : residents) {
            if (batchPaymentRepo.existsByBatchIdAndResidentId(batch.getId(), r.getId())) continue;

            BatchPayment bp = BatchPayment.builder()
                    .batch(batch)
                    .resident(r)
                    .amount(dueAmount)
                    .status(BatchPayment.BatchPaymentStatus.UNPAID)
                    .build();
            batchPaymentRepo.save(bp);
        }

        batchPaymentRepo.flush();
        long paid   = batchPaymentRepo.countPaidByBatchId(batch.getId());
        long unpaid = batchPaymentRepo.countUnpaidByBatchId(batch.getId());
        batch.setPaidCount((int) paid);
        batch.setUnpaidCount((int) unpaid);
        batchRepo.save(batch);
    }

    public MaintenanceBatch updateBatchStatus(Long id, String status) {
        MaintenanceBatch batch = batchRepo.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        batch.setStatus(MaintenanceBatch.BatchStatus.valueOf(status.toUpperCase()));
        return batchRepo.save(batch);
    }

    @Transactional
    public void deleteBatch(Long id) {
        if (!batchRepo.existsById(id))
            throw new CustomException("Batch not found", HttpStatus.NOT_FOUND);
        // Clean up this batch's own payment ledger first (FK to maintenance_batches).
        // Does not touch the regular `payments` table at all.
        batchPaymentRepo.deleteByBatchId(id);
        batchRepo.deleteById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    // NOTE on Family Members: this returns one row per PROPERTY (the Owner
    // resident — residentRepo.findByIsApprovedTrueAndStatus only returns
    // OWNER rows by query design). A Maintenance Batch bills the property
    // once; any Family Member with login access to that property can view
    // and pay the same BatchPayment record on the Owner's behalf (see
    // BatchPaymentController/getEffectiveOwnerResident), so "all assigned
    // owners/family members receive the batch amount" without double-billing
    // a single flat/villa.
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

        // ── FIX: previously read from paymentRepo.countPaidByPaymentMonth(...)
        // / countPendingByPaymentMonth(...), which counted ALL `payments` rows
        // for the calendar month — including regular monthly maintenance —
        // not just this batch. paidCount/unpaidCount are now persisted columns
        // on MaintenanceBatch, maintained exclusively from `batch_payments`
        // rows scoped to this batch's own id (see BatchPaymentService /
        // createBatchPaymentRecords), so they can never include any other
        // batch's or the monthly maintenance's payment records.
        long paid    = batch.getPaidCount()   != null ? batch.getPaidCount()   : 0;
        long pending = batch.getUnpaidCount() != null ? batch.getUnpaidCount() : 0;

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