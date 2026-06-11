package com.resitrack.dto;

import com.resitrack.entity.Receipt;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
 
@Data
@Builder
public class ReceiptResponseDTO {

    private Long id;
    private String receiptNumber;

    private Long   residentId;
    private String residentName;
    private String flatNumber;
    private String flatType;
    private String residentPhone;

    private Long    paymentId;
    private String  transactionId;
    private String  paymentMethod;
    private LocalDate paymentDate;
    private String  paymentMonth;
    private String  paymentYear;

    private BigDecimal paidAmount;
    private BigDecimal lateFeeAmount;
    private BigDecimal totalAmount;

    private String apartmentName;
    private String receiptFooter;

    private LocalDateTime generatedAt;

    public static ReceiptResponseDTO from(Receipt r) {
        BigDecimal late = r.getLateFeeAmount() != null ? r.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal total = r.getPaidAmount() != null ? r.getPaidAmount().add(late) : late;

        String payMonth = null;
        String payYear  = null;
        if (r.getPayment() != null) {
            payMonth = r.getPayment().getPaymentMonth();
            payYear  = r.getPayment().getPaymentYear();
        }

        return ReceiptResponseDTO.builder()
                .id(r.getId())
                .receiptNumber(r.getReceiptNumber())
                .residentId(r.getResident() != null ? r.getResident().getId() : null)
                .residentName(r.getResidentName())
                .flatNumber(r.getFlatNumber())
                .flatType(r.getResident() != null ? r.getResident().getFlatType() : null)
                .residentPhone(r.getResidentPhone())
                .paymentId(r.getPayment() != null ? r.getPayment().getId() : null)
                .transactionId(r.getTransactionId())
                .paymentMethod(r.getPaymentMethod())
                .paymentDate(r.getPaymentDate())
                .paymentMonth(payMonth)
                .paymentYear(payYear)
                .paidAmount(r.getPaidAmount())
                .lateFeeAmount(late)
                .totalAmount(total)
                .apartmentName(r.getApartmentName())
                .receiptFooter(r.getReceiptFooter())
                .generatedAt(r.getGeneratedAt())
                .build();
    }
}
