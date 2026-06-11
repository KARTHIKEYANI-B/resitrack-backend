package com.resitrack.dto;

import com.resitrack.entity.Payment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@Builder
public class PaymentResponseDTO {
    private Long id;
    private String residentName;
    private String flatNumber;
    private String flatType;
    private BigDecimal amount;
    private BigDecimal lateFeeAmount;
    private String paymentStatus;
    private String verificationStatus;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String transactionId;
    private String paymentMonth;
    private String paymentYear;
    private String description;
    private String submittedResidentName;
    private String rejectionReason;
    private boolean adminCreated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponseDTO from(Payment p) {
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .residentName(p.getResident() != null ? p.getResident().getFullName() : "—")
                .flatNumber(p.getResident() != null ? p.getResident().getFlatNumber() : "—")
                .flatType(p.getResident() != null ? p.getResident().getFlatType() : "—")
                .amount(p.getAmount())
                .lateFeeAmount(p.getLateFeeAmount())
                .paymentStatus(p.getPaymentStatus() != null ? p.getPaymentStatus().name() : null)
                .verificationStatus(p.getVerificationStatus() != null ? p.getVerificationStatus().name() : null)
                .paymentDate(p.getPaymentDate())
                .paymentMethod(p.getPaymentMethod())
                .transactionId(p.getTransactionId())
                .paymentMonth(p.getPaymentMonth())
                .paymentYear(p.getPaymentYear())
                .description(p.getDescription())
                .submittedResidentName(p.getSubmittedResidentName())
                .rejectionReason(p.getRejectionReason())
                .adminCreated(Boolean.TRUE.equals(p.getAdminCreated()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
