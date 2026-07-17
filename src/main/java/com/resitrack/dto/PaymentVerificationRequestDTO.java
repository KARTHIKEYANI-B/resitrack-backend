package com.resitrack.dto;

import com.resitrack.entity.PaymentVerificationRequest;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaymentVerificationRequestDTO {

    private Long       id;
    private Long       residentId;
    private String     submittedName;
    private String     flatNumber;
    private String     phoneNumber;
    private BigDecimal paymentAmount;
    private String     transactionId;
    private String     screenshotUrl;   // public URL for screenshot download
    private String     screenshotFileName;
    private String     paymentMonth;
    private String     status;
    private String     rejectionReason;
    private Long       paymentId;       // set once verified
    private String     paymentMethod;   // GPAY | CASH | BANK_TRANSFER
    private Long       paidToAdminId;   // for CASH
    private String     paidToAdminName; // for CASH
    private String     bankName;        // for BANK_TRANSFER (optional)
    private String     submittedByLabel;
    private String     ownerName;

    // ── Multi-Month Maintenance Payment (additive; empty/false for every
    //    request created by the original single-month submit flows) ──────
    private boolean               multiMonth;
    private String                monthsDisplayLabel; // e.g. "May 2026 + Jun 2026"
    private List<MonthAllocationDTO> monthAllocations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentVerificationRequestDTO from(
            PaymentVerificationRequest r, String screenshotUrl) {
        return PaymentVerificationRequestDTO.builder()
                .id(r.getId())
                .residentId(r.getResident() != null ? r.getResident().getId() : null)
                .submittedName(r.getSubmittedName())
                .flatNumber(r.getFlatNumber())
                .phoneNumber(r.getPhoneNumber())
                .paymentAmount(r.getPaymentAmount())
                .transactionId(r.getTransactionId())
                .screenshotUrl(screenshotUrl)
                .screenshotFileName(r.getScreenshotFileName())
                .paymentMonth(r.getPaymentMonth())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .rejectionReason(r.getRejectionReason())
                .paymentId(r.getPaymentId())
                .paymentMethod(r.getPaymentMethod() != null ? r.getPaymentMethod() : "GPAY")
                .paidToAdminId(r.getPaidToAdminId())
                .paidToAdminName(r.getPaidToAdminName())
                .bankName(r.getBankName())
                .multiMonth(Boolean.TRUE.equals(r.getIsMultiMonth()))
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}