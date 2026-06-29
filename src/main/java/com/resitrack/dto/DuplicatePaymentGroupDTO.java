package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Task 1 — Duplicate Payment Cleanup.
 *
 * One entry per (resident, paymentMonth) pair that has more than one PAID
 * payment row recorded against it. Surfaced to Super Admin on Payment
 * Management so the duplicate/fake rows can be reviewed and removed via
 * DELETE /admin/payments/{id}.
 */
@Data
@Builder
public class DuplicatePaymentGroupDTO {
    private Long residentId;
    private String residentName;
    private String flatNumber;
    private String paymentMonth;
    private long duplicateCount;
    private BigDecimal totalAmount;
    private List<PaymentResponseDTO> payments;
}