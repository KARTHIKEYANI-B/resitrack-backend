package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resident-facing (Owner / Family Member) view of one Maintenance Batch Dues row.
 * Shown in the "Maintenance Batch Dues" section of the dashboard.
 */
@Data
@Builder
public class BatchDueDTO {
    private Long batchPaymentId;
    private Long batchId;
    private String batchTitle;     // "Batch Name"
    private String category;
    private String description;
    private BigDecimal amount;     // "Amount"
    private LocalDate dueDate;
    private String status;         // UNPAID | PENDING_VERIFICATION | PAID | REJECTED  → "Status"
    private String paymentMethod;
    private String transactionId;
    private LocalDate submittedDate;
    private LocalDate verifiedDate;
    private String rejectionReason;
}
