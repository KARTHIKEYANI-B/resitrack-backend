package com.resitrack.dto;

import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Receipt;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    // Populated only when the underlying Payment's paymentBatchId has more
    // than one PAID sibling row (a multi-month "Add Payment" submission) —
    // null/empty for an ordinary single-month receipt, so existing callers
    // that ignore this field see no change. When present, one entry per
    // billing month covered by that one payment submission, and
    // batchTotalAmount is the sum across all of them (paidAmount/totalAmount
    // above stay this specific receipt's own single-month figures,
    // unchanged, for backward compatibility). Set by ReceiptService, not by
    // from() below, since building it requires a repository lookup.
    private List<MonthLine> monthBreakdown;
    private BigDecimal      batchTotalAmount;

    private String apartmentName;
    private String receiptFooter;

    private LocalDateTime generatedAt;

    @Data
    @Builder
    public static class MonthLine {
        private String paymentMonth; // "YYYY-MM"
        private BigDecimal amount;
    }

    public static ReceiptResponseDTO from(Receipt r) {
        BigDecimal late = r.getLateFeeAmount() != null ? r.getLateFeeAmount() : BigDecimal.ZERO;
        BigDecimal total = r.getPaidAmount() != null ? r.getPaidAmount().add(late) : late;

        String payMonth = null;
        String payYear  = null;
        if (r.getPayment() != null) {
            payMonth = r.getPayment().getPaymentMonth();
            payYear  = r.getPayment().getPaymentYear();
        }

        // Prefer the frozen value captured on the receipt itself; fall back
        // to the resident's current propertyType only for receipts created
        // before this column existed (their propertyType column is null).
        PropertyType pt = r.getPropertyType();
        if (pt == null && r.getResident() != null) pt = r.getResident().getPropertyType();
        if (pt == null) pt = PropertyType.FLAT;
        String flatLabel = (pt == PropertyType.VILLA ? "Villa " : "Flat ")
                + (r.getFlatNumber() != null ? r.getFlatNumber() : "—");

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
