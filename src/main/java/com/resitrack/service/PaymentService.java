package com.resitrack.service;

import com.resitrack.dto.AdminPaymentRequest;
import com.resitrack.dto.PaymentRequest;
import com.resitrack.dto.PaymentResponseDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository     paymentRepo;
    private final ResidentRepository    residentRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final ReceiptRepository     receiptRepo;
    private final NotificationService   notificationService;

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

    /**
     * Admin Manual Payment Registration — POST /admin/payments
     *
     * Lets an Admin/Super Admin record a monthly maintenance payment on
     * behalf of an owner (e.g. cash collected in person, a bank transfer
     * confirmed outside the app) without that owner ever submitting it
     * themselves.
     *
     * RESIDENT LOOKUP: by phone (unique column) — the frontend collects
     * ownerName for display/confirmation only; phone is the actual key,
     * so a typo'd name never blocks a legitimate payment for the right
     * person while still catching a wrong/unregistered number.
     *
     * VERIFICATION:
     *   verifiedByAdmin = true  → paymentStatus=PAID, verificationStatus=VERIFIED,
     *                             receipt generated immediately (same as approvePayment()).
     *   verifiedByAdmin = false → paymentStatus=PENDING_VERIFICATION,
     *                             verificationStatus=PENDING, awaits the normal
     *                             approve/reject flow — unchanged from today.
     *
     * DUPLICATE PREVENTION: one PAID payment per (resident, paymentMonth).
     * Mirrors the existing existsByResidentIdAndPaymentMonthAndPaymentStatus
     * check already used elsewhere in the codebase — does not touch Maintenance
     * Batch payments (separate batch_payments table/flow, untouched here).
     *
     * adminCreated = true distinguishes this row from owner-submitted
     * payments everywhere downstream (Maintenance Summary, Financial Summary,
     * Dashboard, Payment Management all read straight from the payments table
     * on each request, so no extra "refresh" wiring is needed once the row
     * exists with the correct status/date/month).
     */
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

        Resident resident = residentRepo.findByPhone(req.getOwnerPhone().trim())
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

        // Never create a second PAID record for the same resident + month.
        if (paymentRepo.existsByResidentIdAndPaymentMonthAndPaymentStatus(
                resident.getId(), req.getPaymentMonth(), Payment.PaymentStatus.PAID)) {
            throw new CustomException(
                    "This owner already has a PAID payment recorded for " + req.getPaymentMonth(),
                    HttpStatus.BAD_REQUEST);
        }

        boolean verified = Boolean.TRUE.equals(req.getVerifiedByAdmin());

        String txnId = (req.getTransactionId() != null && !req.getTransactionId().isBlank())
                ? req.getTransactionId().trim()
                : "ADMIN-" + System.currentTimeMillis();

        String year = String.valueOf(req.getPaymentMonth().split("-")[0]);

        // Best-effort link to the currently active Maintenance config for this
        // resident's property type, purely for traceability/reporting — the
        // admin-entered paidAmount is always the amount actually recorded,
        // never recalculated or overridden from this row.
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