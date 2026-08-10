package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminPaymentRequest {

    /** Resident (owner) selected from the searchable Resident dropdown. */
    private Long residentId;

    private String paymentMode;

    private BigDecimal paidAmount;

    private LocalDate paymentDate;

    // Kept for backward compatibility with any other caller of this DTO —
    // PaymentService.resolvePaymentMonths() prefers paymentMonths (below)
    // when present, falling back to this single-value field otherwise.
    private String paymentMonth;

    // Multi-month selection: paidAmount is the TOTAL across every month in
    // this list, allocated oldest-first across each month's own remaining
    // balance (see PaymentService.registerAdminPayment/resolvePaymentMonths).
    private List<String> paymentMonths;

    private Boolean verifiedByAdmin;

    private String transactionId;

    private String description;
}
