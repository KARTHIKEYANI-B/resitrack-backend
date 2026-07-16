package com.resitrack.service;

import com.resitrack.dto.AdminPaymentRequest;
import com.resitrack.dto.PaymentRequest;
import com.resitrack.dto.PaymentResponseDTO;
import com.resitrack.dto.TransactionLedgerEntryDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import com.resitrack.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        return payments.stream().map(PaymentResponseDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponseDTO approvePayment(Long paymentId) {
        Payment p = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        if (p.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException("Payment is not pending verification", HttpStatus.BAD_REQUEST);

        if (p.getResident() != null && p.getPaymentMonth() != null) {
            validateRemainingBalanceAtApproval(p.getResident(), p.getPaymentMonth(), p.getAmount());
        }

        p.setPaymentStatus(Payment.PaymentStatus.PAID);
        p.setVerificationStatus(Payment.VerificationStatus.VERIFIED);
        p.setPaymentDate(LocalDate.now());
        paymentRepo.save(p);

        generateReceipt(p);
        notificationService.sendPaymentApprovedNotification(p);
        return PaymentResponseDTO.from(p);
    }

    @Transactional
    public PaymentResponseDTO rejectPayment(Long paymentId, String reason) {
        Payment p = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        if (p.getPaymentStatus() != Payment.PaymentStatus.PENDING_VERIFICATION)
            throw new CustomException("Payment is not pending verification", HttpStatus.BAD_REQUEST);

        p.setPaymentStatus(Payment.PaymentStatus.PENDING);
        p.setVerificationStatus(Payment.VerificationStatus.REJECTED);
        p.setRejectionReason(reason != null ? reason : "Verification failed");
        paymentRepo.save(p);

        notificationService.sendPaymentRejectedNotification(p);
        return PaymentResponseDTO.from(p);
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
        return paymentRepo.findByResidentIdOrderByCreatedAtDesc(residentId)
                .stream().map(PaymentResponseDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public List<TransactionLedgerEntryDTO> getTransactionLedger() {
        List<TransactionLedgerEntryDTO> entries = new ArrayList<>();

        // ── Income: owner monthly maintenance payments (PAID only) ────────
        for (Payment p : paymentRepo.findByPaymentStatus(Payment.PaymentStatus.PAID)) {
            Resident r = p.getResident();
            LocalDate txnDate = p.getPaymentDate(); // PAID rows always have a paymentDate (set on approve/verify)
            if (txnDate == null) continue; // defensive — should not happen for PAID rows

            entries.add(TransactionLedgerEntryDTO.builder()
                    .date(txnDate)
                    .type("INCOME")
                    .category("Monthly Maintenance")
                    .description(p.getDescription() != null && !p.getDescription().isBlank()
                            ? p.getDescription() : "Monthly maintenance payment")
                    .residentName(r != null ? r.getFullName() : p.getSubmittedResidentName())
                    .flatNumber(r != null ? r.getFlatNumber() : null)
                    .amount(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .paymentMethod(p.getPaymentMethod())
                    .sourceType("MAINTENANCE_PAYMENT")
                    .sourceId(p.getId())
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

    @Transactional
    public PaymentResponseDTO registerAdminPayment(AdminPaymentRequest req) {
        if (req.getOwnerPhone() == null || req.getOwnerPhone().isBlank())
            throw new CustomException("Owner phone number is required", HttpStatus.BAD_REQUEST);
        if (req.getPaidAmount() == null || req.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new CustomException("Payment amount must be greater than zero", HttpStatus.BAD_REQUEST);
        if (req.getPaymentMonth() == null || req.getPaymentMonth().isBlank())
            throw new CustomException("Billing month is required", HttpStatus.BAD_REQUEST);
        if (req.getPaymentDate() == null)
            throw new CustomException("Payment date is required", HttpStatus.BAD_REQUEST);

        if (req.getPaymentMode() != null) {
            String mode = req.getPaymentMode().toUpperCase();
            if (!VALID_PAYMENT_MODES.contains(mode))
                throw new CustomException("Invalid payment method. Use: UPI, BANK_TRANSFER, CASH", HttpStatus.BAD_REQUEST);
        }

        Resident resident = residentRepo.findByPhone(PhoneNormalizer.normalize(req.getOwnerPhone()))
                .orElseThrow(() -> new CustomException(
                        "No registered resident found with this phone number", HttpStatus.NOT_FOUND));

        // Route Family Member -> owner, same as the owner self-pay flow, so
        // the payment always lands on the property's owner record and is
        // picked up by every owner_resident_id-keyed query (Maintenance
        // Summary, Pending Dues, Dashboard).
        if (resident.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER
                && resident.getOwnerResidentId() != null) {
            resident = residentRepo.findById(resident.getOwnerResidentId()).orElse(resident);
        }

        validateRemainingBalance(resident, req.getPaymentMonth(), req.getPaidAmount());

        boolean verified = Boolean.TRUE.equals(req.getVerifiedByAdmin());

        String txnId = (req.getTransactionId() != null && !req.getTransactionId().isBlank())
                ? req.getTransactionId().trim()
                : "ADMIN-" + System.currentTimeMillis();

        String year = String.valueOf(req.getPaymentMonth().split("-")[0]);

        Maintenance maint = getActiveMaintenanceConfigForResident(resident);

        Payment p = Payment.builder()
                .resident(resident)
                .maintenance(maint)
                .amount(req.getPaidAmount())
                .lateFeeAmount(BigDecimal.ZERO)
                .paymentDate(verified ? req.getPaymentDate() : null)
                .paymentMethod(req.getPaymentMode())
                .transactionId(txnId)
                .submittedResidentName(req.getOwnerName())
                .paymentStatus(verified ? Payment.PaymentStatus.PAID : Payment.PaymentStatus.PENDING_VERIFICATION)
                .verificationStatus(verified ? Payment.VerificationStatus.VERIFIED : Payment.VerificationStatus.PENDING)
                .paymentMonth(req.getPaymentMonth())
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

        return PaymentResponseDTO.from(p);
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
     * @param paymentId   the payment to delete
     * @param callerEmail the email of the authenticated admin performing
     *                    the deletion (from the JWT/Authentication), used
     *                    to verify Super Admin status server-side
     */
    @Transactional
    public void deletePayment(Long paymentId, String callerEmail) {
        Admin caller = adminRepo.findByEmail(callerEmail)
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));

        if (!caller.isSuperAdmin())
            throw new CustomException(
                    "Only Super Admin can delete payment records.", HttpStatus.FORBIDDEN);

        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        receiptRepo.findByPaymentId(paymentId).ifPresent(receiptRepo::delete);

        paymentRepo.delete(payment);
        paymentRepo.flush();

        log.info("Payment {} (resident={}, amount={}, paymentMonth={}) deleted by Super Admin {}",
                paymentId,
                payment.getResident() != null ? payment.getResident().getId() : null,
                payment.getAmount(), payment.getPaymentMonth(), callerEmail);
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