package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Admin-facing row for Admin → Maintenance → Maintenance Batch → "Paid List",
 * and also used (with `status` populated) for the Admin → Payment
 * Verification screen's batch-payment rows.
 */
@Data
@Builder
public class PaidListEntryDTO {
    private Long batchPaymentId;
    private Long batchId;
    private String batchTitle;
    private String residentName;       // kept for backward-compat (= ownerName)
    private String ownerName;          // "Owner Name" — the property owner (always set)
    private String familyMemberName;   // "Family Member Name" — set only if paid by a Family Member
    private String flatNumber;         // "Flat/Villa Number"
    private BigDecimal amount;
    private String status;
    private LocalDate paidDate;        // "Payment Date"
    private LocalDate submittedDate;
    private String paidBy;             // "Paid By" — name of whoever actually paid
    private String paidByRole;         // OWNER | FAMILY_MEMBER — "(Owner / Family Member)"
    private String paymentMethod;
    private String transactionId;
}