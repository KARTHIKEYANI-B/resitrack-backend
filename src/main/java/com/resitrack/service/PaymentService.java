package com.resitrack.service;

import com.resitrack.dto.AdminPaymentRequest;
import com.resitrack.dto.PaymentRequest;
import com.resitrack.dto.PaymentResponseDTO;
import com.resitrack.dto.ResidentMaintenanceInfoDTO;
import com.resitrack.dto.TransactionLedgerEntryDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import com.resitrack.util.NaturalOrderComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository      paymentRepo;
    private final ResidentRepository     residentRepo;
    private final MaintenanceRepository  maintenanceRepo;
    private final MaintenanceService     maintenanceService;
    private final ReceiptRepository      receiptRepo;
    private final NotificationService    notificationService;
    private final ExpenseRepository      expenseRepo;
    private final BatchPaymentRepository batchPaymentRepo;
    private final AdminRepository        adminRepo;

    private static final List<String> VALID_PAYMENT_MODES = List.of("UPI", "BANK_TRANSFER", "CASH");

    public List<PaymentResponseDTO> getAllPayments(String status) {
        List<Payment> payments = (status != null && !status.isBlank())
                ? paymentRepo.findByPaymentStatus(Payment.PaymentStatus.valueOf(status.toUpperCase()))
                : paymentRepo.findAllByOrderByCreatedAtDesc();
        return dedupeByBatch(payments);
    }

    // Every sibling row from the same multi-month "Add Payment" batch that
    // currently shares `payment`'s own status — i.e. the exact set the
    // Pending Verification list's single consolidated row represents.
    // Returns just [payment] for an ordinary single-month entry.
    private List<Payment> batchSiblingsInSameStatus(Payment payment) {
        if (payment.getPaymentBatchId() == null) return List.of(payment);
        return paymentRepo.findByPaymentBatchIdOrderByPaymentMonthAsc(payment.getPaymentBatchId())
                .stream()
                .filter(p -> p.getPaymentStatus() == payment.getPaymentStatus())
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponseDTO approvePayment(Long paymentId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        if (payment.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException("Payment is not pending verification", HttpStatus.BAD_REQUEST);

        if (p.getResident() != null && p.getPaymentMonth() != null) {
            validateRemainingBalanceAtApproval(p.getResident(), p.getPaymentMonth(), p.getAmount());
        }

        LocalDate today = LocalDate.now();
        for (Payment p : toApprove) {
            p.setPaymentStatus(Payment.PaymentStatus.PAID);
            p.setVerificationStatus(Payment.VerificationStatus.VERIFIED);
            p.setPaymentDate(today);
            paymentRepo.save(p);

            generateReceipt(p);
            notificationService.sendPaymentApprovedNotification(p);
        }
        return dedupeByBatch(toApprove).get(0);
    }

    @Transactional
    public PaymentResponseDTO rejectPayment(Long paymentId, String reason) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        if (payment.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException("Payment is not pending verification", HttpStatus.BAD_REQUEST);

        // See approvePayment() above — reject must cover every sibling month
        // in the batch, matching what the consolidated row displayed.
        List<Payment> toReject = batchSiblingsInSameStatus(payment);
        String rejectionReason = reason != null ? reason : "Verification failed";

        for (Payment p : toReject) {
            p.setPaymentStatus(Payment.PaymentStatus.PENDING);
            p.setVerificationStatus(Payment.VerificationStatus.REJECTED);
            p.setRejectionReason(rejectionReason);
            paymentRepo.save(p);

            notificationService.sendPaymentRejectedNotification(p);
        }
        return dedupeByBatch(toReject).get(0);
    }

    @Transactional
    public PaymentResponseDTO submitForVerification(Long residentId, PaymentRequest req) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        Maintenance m = maintenanceRepo.findById(req.getMaintenanceId())
                .orElseThrow(() -> new CustomException("Maintenance not found", HttpStatus.NOT_FOUND));

        if (req.getPaymentMethod() != null) {
            String method = req.getPaymentMethod().toUpperCase();
            if (!VALID_PAYMENT_MODES.contains(method))
                throw new CustomException("Invalid payment method. Use: UPI, BANK_TRANSFER, CASH", HttpStatus.BAD_REQUEST);
        }

        BigDecimal lateFee = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(m.getLateFeeEnabled()) && LocalDate.now().isAfter(m.getDueDate()))
            lateFee = m.getLateFee() != null ? m.getLateFee() : BigDecimal.ZERO;

        String txnId = (req.getTransactionId() != null && !req.getTransactionId().isBlank())
                ? req.getTransactionId().trim()
                : "CASH-" + System.currentTimeMillis();

        String currentMonth = LocalDate.now().getYear() + "-"
                + String.format("%02d", LocalDate.now().getMonthValue());

        // Allow installment payments here too: only reject once the required
        // amount has already been fully covered by VERIFIED (PAID) payments.
        validateRemainingBalance(r, currentMonth, m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO);

        Payment p = Payment.builder()
                .resident(r).maintenance(m)
                .amount(m.getAmount()).lateFeeAmount(lateFee)
                .paymentDate(null)
                .paymentMethod(req.getPaymentMethod())
                .transactionId(txnId)
                .submittedResidentName(req.getResidentName())
                .submittedRegisterNumber(req.getRegisterNumber())
                .paymentStatus(Payment.PaymentStatus.PENDING_VERIFICATION)
                .verificationStatus(Payment.VerificationStatus.PENDING)
                .paymentMonth(currentMonth)
                .paymentYear(String.valueOf(LocalDate.now().getYear()))
                .adminCreated(false)
                .build();

        paymentRepo.save(p);
        notificationService.sendPaymentVerificationRequest(p);
        return PaymentResponseDTO.from(p);
    }

    public List<PaymentResponseDTO> getResidentPayments(Long residentId) {
        return dedupeByBatch(paymentRepo.findByResidentIdOrderByCreatedAtDesc(residentId));
    }

    /**
     * Collapses rows sharing a paymentBatchId (a multi-month "Add Payment"
     * submission — see Payment.paymentBatchId) down to one representative
     * entry per (batchId, paymentStatus) group, for "list of payments"
     * views: Payment Management's ledger/pending list and a resident's
     * payment history. Grouped by status too, not just batchId, so a batch
     * that ends up split across statuses (e.g. 2 of 3 months approved, 1
     * rejected — approvePayment/rejectPayment act on individual rows) shows
     * each status's own subset combined correctly, rather than mixing a
     * rejected row's amount into a "paid" total.
     *
     * The representative row is the earliest paymentMonth in its group,
     * carrying the group's combined monthBreakdown/batchTotalAmount (see
     * PaymentResponseDTO) when the group has 2+ rows; a solo row (no batch,
     * or the only row left in its status group) passes through unchanged.
     * Screens that need the real per-month split (Paid/Unpaid Details,
     * Maintenance Summary, Financial Summary) query Payment rows directly
     * and are entirely unaffected by this.
     */
    private List<PaymentResponseDTO> dedupeByBatch(List<Payment> payments) {
        Map<String, List<Payment>> groups = new LinkedHashMap<>();
        for (Payment p : payments) {
            String key = p.getPaymentBatchId() != null
                    ? p.getPaymentBatchId() + "|" + p.getPaymentStatus()
                    : "single-" + p.getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<PaymentResponseDTO> result = new ArrayList<>();
        for (List<Payment> group : groups.values()) {
            group.sort(Comparator.comparing(Payment::getPaymentMonth,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            Payment representative = group.get(0);
            PaymentResponseDTO dto = PaymentResponseDTO.from(representative);

            if (group.size() > 1) {
                List<PaymentResponseDTO.MonthLine> lines = group.stream()
                        .map(p -> PaymentResponseDTO.MonthLine.builder()
                                .paymentMonth(p.getPaymentMonth())
                                .amount(p.getAmount())
                                .build())
                        .collect(Collectors.toList());
                BigDecimal batchTotal = group.stream()
                        .map(Payment::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                dto.setMonthBreakdown(lines);
                dto.setBatchTotalAmount(batchTotal);
            }
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public List<TransactionLedgerEntryDTO> getTransactionLedger() {
        List<TransactionLedgerEntryDTO> entries = new ArrayList<>();

        // ── Income: owner monthly maintenance payments (PAID only) ────────
        // Multi-month "Add Payment" submissions (see Payment.paymentBatchId)
        // collapse to one ledger row covering every selected month and the
        // combined total, instead of one row per month — see dedupeByBatch().
        for (PaymentResponseDTO dto : dedupeByBatch(paymentRepo.findByPaymentStatus(Payment.PaymentStatus.PAID))) {
            LocalDate txnDate = dto.getPaymentDate(); // PAID rows always have a paymentDate (set on approve/verify)
            if (txnDate == null) continue; // defensive — should not happen for PAID rows

            boolean isBatch = dto.getMonthBreakdown() != null && !dto.getMonthBreakdown().isEmpty();

            entries.add(TransactionLedgerEntryDTO.builder()
                    .date(txnDate)
                    .type("INCOME")
                    .category("Monthly Maintenance")
                    .description(dto.getDescription() != null && !dto.getDescription().isBlank()
                            ? dto.getDescription() : "Monthly maintenance payment")
                    .residentName(dto.getResidentName())
                    .flatNumber(dto.getFlatNumber())
                    .amount(isBatch ? dto.getBatchTotalAmount()
                            : (dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO))
                    .paymentMethod(dto.getPaymentMethod())
                    .sourceType("MAINTENANCE_PAYMENT")
                    .sourceId(dto.getId())
                    .paymentBatchId(dto.getPaymentBatchId())
                    .monthBreakdown(dto.getMonthBreakdown())
                    .batchTotalAmount(dto.getBatchTotalAmount())
                    .build());
        }

        // ── Income: maintenance batch payments (PAID only) ─────────────────
        for (BatchPayment bp : batchPaymentRepo.findAllPaidOrderByVerifiedDateDesc()) {
            LocalDate txnDate = bp.getVerifiedDate() != null ? bp.getVerifiedDate() : bp.getSubmittedDate();
            if (txnDate == null) continue; // defensive — should not happen for PAID rows

            Resident r = bp.getResident();
            MaintenanceBatch batch = bp.getBatch();
            String category = batch != null && batch.getCategory() != null
                    ? "Maintenance Batch: " + batch.getCategory()
                    : "Maintenance Batch";
            String description = batch != null && batch.getTitle() != null
                    ? batch.getTitle() : "Maintenance batch payment";

            entries.add(TransactionLedgerEntryDTO.builder()
                    .date(txnDate)
                    .type("INCOME")
                    .category(category)
                    .description(description)
                    .residentName(r != null ? r.getFullName() : bp.getPaidByName())
                    .flatNumber(r != null ? r.getFlatNumber() : null)
                    .amount(bp.getAmount() != null ? bp.getAmount() : BigDecimal.ZERO)
                    .paymentMethod(bp.getPaymentMethod())
                    .sourceType("BATCH_PAYMENT")
                    .sourceId(bp.getId())
                    .build());
        }

        // ── Expense: every expense record ──────────────────────────────────
        for (Expense e : expenseRepo.findAll()) {
            if (e.getExpenseDate() == null) continue; // defensive — column is NOT NULL, but guard anyway

            entries.add(TransactionLedgerEntryDTO.builder()
                    .date(e.getExpenseDate())
                    .type("EXPENSE")
                    .category(e.getCategory())
                    .description(e.getDescription() != null && !e.getDescription().isBlank()
                            ? e.getDescription() : e.getExpenseName())
                    .residentName(null)
                    .flatNumber(null)
                    .amount(e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                    .paymentMethod(e.getPaymentMethod())
                    .sourceType("EXPENSE")
                    .sourceId(e.getId())
                    .build());
        }

        // ── Sort by date descending (latest first), then assign serial numbers ──
        entries.sort(Comparator.comparing(TransactionLedgerEntryDTO::getDate).reversed());
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setSerialNo(i + 1);
        }

        return entries;
    }

    /** One resolved (month, amount) allocation — see resolveAdminMonthAllocations(). */
    private record MonthAmount(String month, BigDecimal amount) {}

    /**
     * Creates one Payment row per selected billing month that actually
     * receives money (multi-month selection support). req.getPaidAmount()
     * is now the TOTAL the admin is recording across every selected month
     * (typically auto-filled by the frontend as monthly rate × month count
     * — see lookupResidentMaintenanceInfo() below), not a per-month amount.
     * It is validated against, and sequentially allocated across, each
     * month's own remaining balance in chronological order (oldest first):
     * a month already fully paid is skipped entirely, a month with a prior
     * partial payment only receives up to its own remaining balance, and
     * allocation stops as soon as the entered total is exhausted — so a
     * partial multi-month total (e.g. ₹5,000 across 3 months of ₹3,000
     * each) still creates only as many rows as it actually covers, exactly
     * mirroring how a partial single-month payment already worked before
     * multi-month selection existed.
     *
     * No schema change was needed for this: the payments table already
     * allows multiple rows per resident+paymentMonth (that's how partial/
     * installment payments already worked), so "N months selected" simply
     * creates up to N of the same row shape that already existed, each
     * picked up by every existing paymentMonth-keyed query (Financial
     * Summary, Maintenance Summary, Paid/Unpaid Details, Dashboard) exactly
     * as a single-month admin payment already was.
     */
    @Transactional
    public List<PaymentResponseDTO> registerAdminPayment(AdminPaymentRequest req) {
        if (req.getOwnerPhone() == null || req.getOwnerPhone().isBlank())
            throw new CustomException("Owner phone number is required", HttpStatus.BAD_REQUEST);
        if (req.getPaidAmount() == null || req.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Payment amount must be greater than zero", HttpStatus.BAD_REQUEST);

        List<String> months = resolvePaymentMonths(req);
        if (months.isEmpty())
            throw new CustomException("Billing month is required", HttpStatus.BAD_REQUEST);

        if (req.getPaymentDate() == null)
            throw new CustomException("Payment date is required", HttpStatus.BAD_REQUEST);

        if (req.getPaymentMode() != null) {
            String mode = req.getPaymentMode().toUpperCase();
            if (!VALID_PAYMENT_MODES.contains(mode))
                throw new CustomException("Invalid payment method. Use: UPI, BANK_TRANSFER, CASH", HttpStatus.BAD_REQUEST);
        }

        Resident resident = resolveResidentForPayment(req.getOwnerPhone());

        // Chronological order (paymentMonth is always "YYYY-MM", so plain
        // string sort is chronologically correct) — oldest dues get paid
        // first when the entered total doesn't cover every selected month.
        List<String> sortedMonths = months.stream().sorted().collect(Collectors.toList());

        // ── Validate against the TOTAL due across every selected month,
        // not any single month's balance (replaces the old per-month check
        // that produced "Amount exceeds the remaining balance of XXXX.XX
        // for this month" even when the total across multiple months was
        // perfectly valid). ──────────────────────────────────────────────
        Map<String, BigDecimal> remainingByMonth = remainingBalanceByMonth(resident, sortedMonths);
        BigDecimal totalRemaining = remainingByMonth.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Maintenance for the selected month(s) has already been fully paid.", HttpStatus.BAD_REQUEST);
        }
        if (req.getPaidAmount().compareTo(totalRemaining) > 0) {
            throw new CustomException(
                    "Amount exceeds the total remaining balance of "
                            + totalRemaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for the selected month(s).", HttpStatus.BAD_REQUEST);
        }

        boolean verified = Boolean.TRUE.equals(req.getVerifiedByAdmin());
        Maintenance maint = getActiveMaintenanceConfigForResident(resident);
        String baseTxnId = (req.getTransactionId() != null && !req.getTransactionId().isBlank())
                ? req.getTransactionId().trim()
                : null;
        long batchTimestamp = System.currentTimeMillis();
        // Shared by every row this call creates — lets ReceiptService find
        // all sibling months later and render them as one consolidated
        // receipt instead of N separate single-month ones. Assigned even
        // for a single selected month, so "part of a batch" is simply
        // "batchId has more than one PAID row" rather than a special case.
        String batchId = UUID.randomUUID().toString();

        List<PaymentResponseDTO> results = new ArrayList<>();
        BigDecimal amountLeft = req.getPaidAmount();
        int rowIndex = 0;

        for (String month : sortedMonths) {
            if (amountLeft.compareTo(BigDecimal.ZERO) <= 0) break; // entered total fully allocated

            BigDecimal remaining = remainingByMonth.getOrDefault(month, BigDecimal.ZERO);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue; // this month already fully paid — skip, no ₹0 row

            BigDecimal alloc = remaining.min(amountLeft);
            amountLeft = amountLeft.subtract(alloc);

            String year = month.split("-")[0];

            // transactionId is DB-unique, so it can't be reused verbatim
            // across multiple rows in one admin entry. The first row
            // created keeps the admin-provided reference exactly as typed
            // (so it still matches a bank/UPI statement lookup); every
            // additional row gets a distinguishing suffix. Auto-generated
            // IDs (no reference provided) get a per-row suffix too, since
            // System.currentTimeMillis() alone could collide across loop
            // iterations landing in the same millisecond.
            String txnId;
            if (baseTxnId != null) {
                txnId = (rowIndex == 0) ? baseTxnId : baseTxnId + "-" + (rowIndex + 1);
            } else {
                txnId = "ADMIN-" + batchTimestamp + "-" + (rowIndex + 1);
            }
            rowIndex++;

            Payment p = Payment.builder()
                    .resident(resident)
                    .maintenance(maint)
                    .amount(alloc)
                    .lateFeeAmount(BigDecimal.ZERO)
                    .paymentDate(verified ? req.getPaymentDate() : null)
                    .paymentMethod(req.getPaymentMode())
                    .transactionId(txnId)
                    .paymentBatchId(batchId)
                    .submittedResidentName(req.getOwnerName())
                    .paymentStatus(verified ? Payment.PaymentStatus.PAID : Payment.PaymentStatus.PENDING_VERIFICATION)
                    .verificationStatus(verified ? Payment.VerificationStatus.VERIFIED : Payment.VerificationStatus.PENDING)
                    .paymentMonth(month)
                    .paymentYear(year)
                    .description(req.getDescription())
                    .adminCreated(true)
                    .build();

            paymentRepo.save(p);

            if (verified) {
                generateReceipt(p);
                notificationService.sendPaymentApprovedNotification(p);
            } else {
                notificationService.sendPaymentVerificationRequest(p);
            }

            results.add(PaymentResponseDTO.from(p));
        }

        return results;
    }

    /**
     * Owner-lookup step for the "Add Payment" form's auto-fill: resolves
     * the resident by residentId (preferred — direct, from the resident
     * search/select dropdown, no ambiguity) or, for backward compatibility,
     * by phone number (same routing rule as registerAdminPayment — a
     * Family Member's phone resolves to the property's owner) — and
     * returns their current monthly maintenance rate so the frontend can
     * pre-fill Amount = rate × selected-month-count. Read-only — creates
     * nothing, validates nothing about payments.
     */
    public Map<String, Object> lookupResidentMaintenanceInfo(Long residentId, String ownerPhone) {
        Resident resident;
        if (residentId != null) {
            resident = residentRepo.findById(residentId)
                    .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
        } else if (ownerPhone != null && !ownerPhone.isBlank()) {
            resident = resolveResidentForPayment(ownerPhone);
        } else {
            throw new CustomException("Resident ID or phone number is required", HttpStatus.BAD_REQUEST);
        }

        BigDecimal monthlyMaintenance = maintenanceService.getRequiredMaintenanceAmountFor(resident);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("residentId",         resident.getId());
        result.put("residentName",       resident.getFullName());
        result.put("flatNumber",         resident.getFlatNumber());
        result.put("phone",              resident.getPhone());
        result.put("monthlyMaintenance", monthlyMaintenance);
        return result;
    }

    /**
     * Resolves a resident by phone for a payment action — shared by
     * registerAdminPayment and lookupResidentMaintenanceInfo so both use
     * the exact same lookup/routing rule (a Family Member's phone number
     * always resolves to the property's owner record, since that's whose
     * paymentMonth-keyed balance actually gets credited).
     */
    private Resident resolveResidentForPayment(String ownerPhone) {
        Resident resident = residentRepo.findByPhone(PhoneNormalizer.normalize(ownerPhone))
                .orElseThrow(() -> new CustomException(
                        "No registered resident found with this phone number", HttpStatus.NOT_FOUND));

        if (resident.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER
                && resident.getOwnerResidentId() != null) {
            resident = residentRepo.findById(resident.getOwnerResidentId()).orElse(resident);
        }
        return resident;
    }

    /**
     * Per-month remaining balance (required − already-PAID) for a resident
     * across the given months, floored at zero per month (never negative —
     * an overpaid month simply contributes 0 to the total, same as the old
     * single-month validateRemainingBalance's floor). Uses the resident's
     * current maintenance rate for every month, same as the rest of this
     * class already does (no historical per-month rate lookup — unchanged
     * existing behavior/limitation, not something this feature changes).
     */
    private Map<String, BigDecimal> remainingBalanceByMonth(Resident resident, List<String> months) {
        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String month : months) {
            if (required.compareTo(BigDecimal.ZERO) <= 0) {
                result.put(month, BigDecimal.ZERO);
                continue;
            }
            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), month);
            BigDecimal totalPaid = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;
            BigDecimal remaining = required.subtract(totalPaid);
            result.put(month, remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * Resolves the billing months to create Payment rows for — prefers the
     * multi-select paymentMonths list, falling back to the original single
     * paymentMonth field so any other caller of this DTO keeps working
     * unchanged. De-duplicates (preserving selection order) so accidentally
     * selecting the same month twice in the UI doesn't create two rows for it.
     */
    private List<String> resolvePaymentMonths(AdminPaymentRequest req) {
        List<String> raw;
        if (req.getPaymentMonths() != null && !req.getPaymentMonths().isEmpty()) {
            raw = req.getPaymentMonths();
        } else if (req.getPaymentMonth() != null && !req.getPaymentMonth().isBlank()) {
            raw = List.of(req.getPaymentMonth());
        } else {
            return List.of();
        }
        return raw.stream()
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Validates the admin's selected billing months and allocates the
     * single entered paidAmount across them oldest-month-first — the same
     * settlement order the resident's own multi-month "Pay Maintenance"
     * flow uses (PaymentVerificationService.buildValidatedMonthAllocations)
     * — so a partial amount still lands correctly on the earliest unpaid
     * month(s) rather than being split blindly across all of them.
     *
     * Validation is against the TOTAL remaining balance across every
     * selected month (required × months − already paid for each), not any
     * single month in isolation — this replaces the old per-month-only
     * "Amount exceeds the remaining balance of X for this month" check,
     * which no longer applies once more than one month can be selected.
     *
     * A month already fully covered by prior payments contributes zero and
     * is simply skipped when creating rows — it never blocks the other
     * selected months, mirroring how the resident-side flow treats an
     * already-settled month within a multi-month selection.
     */
    private List<MonthAmount> resolveAdminMonthAllocations(
            Resident resident, List<String> months, BigDecimal paidAmount) {

        List<String> distinctMonths = months.stream()
                .filter(m -> m != null && !m.isBlank())
                .distinct().sorted().collect(Collectors.toList());

        if (distinctMonths.isEmpty())
            throw new CustomException("Select at least one billing month", HttpStatus.BAD_REQUEST);

        for (String m : distinctMonths) {
            if (!m.matches("\\d{4}-\\d{2}"))
                throw new CustomException("Invalid billing month: " + m, HttpStatus.BAD_REQUEST);
        }

        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);

        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            // Nothing configured — nothing to gate, same fallback the
            // previous single-month flow already relied on; the full
            // entered amount is recorded against the earliest selected month.
            return List.of(new MonthAmount(distinctMonths.get(0), paidAmount));
        }

        LinkedHashMap<String, BigDecimal> remainingByMonth = new LinkedHashMap<>();
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (String month : distinctMonths) {
            Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), month);
            BigDecimal paidSoFar = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;
            BigDecimal remaining = required.subtract(paidSoFar).max(BigDecimal.ZERO);
            remainingByMonth.put(month, remaining);
            totalRemaining = totalRemaining.add(remaining);
        }

        if (totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Maintenance for the selected month(s) has already been fully paid.", HttpStatus.BAD_REQUEST);
        }

        if (paidAmount.compareTo(totalRemaining) > 0) {
            throw new CustomException(
                    "Amount exceeds the total remaining balance of "
                            + totalRemaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for the selected month(s).", HttpStatus.BAD_REQUEST);
        }

        List<MonthAmount> allocations = new ArrayList<>();
        BigDecimal amountLeft = paidAmount;
        for (String month : distinctMonths) {
            if (amountLeft.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal remaining = remainingByMonth.get(month);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue; // already fully paid — skip
            BigDecimal allocate = amountLeft.min(remaining);
            allocations.add(new MonthAmount(month, allocate));
            amountLeft = amountLeft.subtract(allocate);
        }
        return allocations;
    }

    /**
     * Backs GET /admin/payments/eligible-residents — powers the "Record
     * Payment" form's searchable resident dropdown (search by owner name
     * or flat/villa number) and, once a resident is selected, the Amount
     * auto-calculation (monthlyMaintenanceAmount × selected months) — all
     * from this one list, fetched once when the form opens, so selecting a
     * resident never needs a separate round-trip. Read-only; scoped to
     * active, approved OWNER residents — the same
     * findAllActiveApprovedOwners() scope Financial Summary, Maintenance
     * Summary and Paid/Unpaid Details already use.
     */
    @Transactional(readOnly = true)
    public List<ResidentMaintenanceInfoDTO> getEligibleResidentsForPayment() {
        return residentRepo.findAllActiveApprovedOwners().stream()
                .sorted(Comparator.comparing(
                        (Resident r) -> r.getFlatNumber() != null ? r.getFlatNumber() : "",
                        NaturalOrderComparator.INSTANCE))
                .map(r -> ResidentMaintenanceInfoDTO.builder()
                        .residentId(r.getId())
                        .ownerName(r.getFullName())
                        .flatNumber(r.getFlatNumber())
                        .phone(r.getPhone())
                        .monthlyMaintenanceAmount(maintenanceService.getRequiredMaintenanceAmountFor(r))
                        .build())
                .collect(Collectors.toList());
    }

    /** Mirrors MaintenanceService.getActiveMaintenanceConfigFor(Resident) without
     *  introducing a cross-service dependency — same fallback rule: prefer the
     *  resident's own property-type rate, fall back to the legacy shared row. */
    private Maintenance getActiveMaintenanceConfigForResident(Resident resident) {
        if (resident.getPropertyType() != null) {
            List<Maintenance> matches = maintenanceRepo
                    .findActiveByPropertyTypeOrderByCreatedAtDesc(resident.getPropertyType());
            if (!matches.isEmpty()) return matches.get(0);
        }
        return maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true)
                .filter(m -> m.getPropertyType() == null)
                .orElse(null);
    }

    private void validateRemainingBalance(Resident resident, String paymentMonth, BigDecimal requestedAmount) {
        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        if (required.compareTo(BigDecimal.ZERO) <= 0) return; // nothing configured — nothing to gate

        Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), paymentMonth);
        BigDecimal totalPaid = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;

        BigDecimal remaining = required.subtract(totalPaid);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Maintenance for this month has already been fully paid.", HttpStatus.BAD_REQUEST);
        }

        if (requestedAmount.compareTo(remaining) > 0) {
            throw new CustomException(
                    "Amount exceeds the remaining balance of " + remaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for this month.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRemainingBalanceAtApproval(
            Resident resident, String paymentMonth, BigDecimal requestedAmount) {
        BigDecimal required = maintenanceService.getRequiredMaintenanceAmountFor(resident);
        if (required.compareTo(BigDecimal.ZERO) <= 0) return; // nothing configured — nothing to gate

        Double paidRaw = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(resident.getId(), paymentMonth);
        BigDecimal totalPaid = paidRaw != null ? BigDecimal.valueOf(paidRaw) : BigDecimal.ZERO;

        BigDecimal remaining = required.subtract(totalPaid);
        BigDecimal amount = requestedAmount != null ? requestedAmount : BigDecimal.ZERO;

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(
                    "Cannot approve — maintenance for " + paymentMonth + " has already been fully paid " +
                    "by another approved payment. This appears to be a duplicate; reject it instead.",
                    HttpStatus.BAD_REQUEST);
        }

        if (amount.compareTo(remaining) > 0) {
            throw new CustomException(
                    "Cannot approve — this amount exceeds the remaining balance of "
                            + remaining.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " for " + paymentMonth + ". Another payment may have already been approved " +
                            "for this month; reject this one if it is a duplicate.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Super Admin payment deletion.
     *
     * Permanently removes a payment record from the database, and with it
     * removes that amount from EVERY downstream total, because every
     * consumer (Admin Dashboard, Maintenance Summary, Paid/Unpaid Details,
     * Financial Summary, Payment Management) reads straight from the
     * `payments` table on each request — there is no cached/duplicated copy
     * of a payment's amount anywhere else to also clean up. Totals
     * recalculate automatically on the next read; no separate
     * "recalculate" step exists or is needed.
     *
     * Restricted to Super Admin (same permission model as
     * AdminAccountController's destructive admin-account actions): a
     * regular Admin, Owner, Family Member, or Security account can never
     * delete a payment record, since deletion is irreversible and directly
     * changes collection totals shown to everyone. This is enforced here,
     * server-side, regardless of what the calling UI shows or hides.
     *
     * The associated Receipt (if one was generated for this payment) is
     * deleted first and explicitly, rather than relying solely on the
     * database's ON DELETE CASCADE on receipts.payment_id — this guarantees
     * the deletion is visible to Hibernate's own session/cache immediately,
     * not just at the database level, and avoids a stale Receipt read
     * inside the same transaction.
     *
     * Batch-aware: if the target payment is part of a multi-month "Add
     * Payment" submission (Payment.paymentBatchId), every sibling row that
     * shares that batchId AND the target's own paymentStatus is deleted
     * too — matching exactly the set of rows dedupeByBatch() folded into
     * the single ledger/list entry the admin actually clicked delete on
     * (grouped by status, not just batchId, so this never reaches into a
     * differently-resolved sibling, e.g. one already rejected separately).
     * A payment with no batchId (or the only remaining row in its status
     * group) deletes just itself, unchanged from before.
     *
     * @param paymentId   the payment to delete (or the representative row
     *                    of a batch, from a list view)
     * @param callerEmail the email of the authenticated admin performing
     *                    the deletion (from the JWT/Authentication), used
     *                    to verify Super Admin status server-side
     */
    @Transactional
    public void deletePayment(Long paymentId, String callerEmail) {
        Admin caller = adminRepo.findByEmail(callerEmail)
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));

        // System Owner has every permission Super Admin has (see Admin.java's
        // `systemOwner` field javadoc), so this accepts either.
        if (!caller.isSuperAdmin() && !caller.isSystemOwner())
            throw new CustomException(
                    "Only Super Admin can delete payment records.", HttpStatus.FORBIDDEN);

        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        List<Payment> toDelete = batchSiblingsInSameStatus(payment);

        for (Payment p : toDelete) {
            receiptRepo.findByPaymentId(p.getId()).ifPresent(receiptRepo::delete);
        }
        paymentRepo.deleteAll(toDelete);
        paymentRepo.flush();

        log.info("Payment {} (resident={}, amount={}, paymentMonth={}) deleted by Super Admin {}{}",
                paymentId,
                payment.getResident() != null ? payment.getResident().getId() : null,
                payment.getAmount(), payment.getPaymentMonth(), callerEmail,
                toDelete.size() > 1
                        ? " — batch delete, " + toDelete.size() + " months (" + payment.getPaymentBatchId() + ")"
                        : "");
    }

    private void generateReceipt(Payment payment) {
        if (receiptRepo.findByPaymentId(payment.getId()).isPresent()) return;

        String receiptNo = "REC-" + LocalDate.now().getYear()
                + "-" + String.format("%06d", System.currentTimeMillis() % 1_000_000);

        Resident r       = payment.getResident();
        BigDecimal lf    = payment.getLateFeeAmount() != null ? payment.getLateFeeAmount() : BigDecimal.ZERO;

        Receipt receipt = Receipt.builder()
                .receiptNumber(receiptNo)
                .payment(payment).resident(r)
                .residentName(r.getFullName())
                .flatNumber(r.getFlatNumber())
                .propertyType(r.getPropertyType())
                .residentPhone(r.getPhone())
                .paymentDate(payment.getPaymentDate())
                .paidAmount(payment.getAmount())
                .lateFeeAmount(lf)
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .apartmentName("R R Dhurya Owners Welfare Association")
                .receiptFooter("Thank you for your payment.")
                .build();

        receiptRepo.save(receipt);
    }
}