package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminPaymentRequest {

    private String ownerPhone;

    private String ownerName;

    private String paymentMode;

    private BigDecimal paidAmount;

    private LocalDate paymentDate;

    // Kept for backward compatibility with any other caller of this DTO —
    // PaymentService.resolvePaymentMonths() prefers paymentMonths (below)
    // when present, falling back to this single-value field otherwise.
    private String paymentMonth;

    // Multi-month selection: the same paidAmount is applied to EACH month
    // in this list (one Payment row created per month), not split/divided
    // across them.
    private List<String> paymentMonths;

    private Boolean verifiedByAdmin;

    private String transactionId;

    private String description;
}
