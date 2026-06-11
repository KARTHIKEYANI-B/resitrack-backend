package com.resitrack.service;

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
                .receiptFooter("Thank you for your payment. This is a computer-generated receipt.")
                .build();

        receiptRepo.save(receipt);
    }
}