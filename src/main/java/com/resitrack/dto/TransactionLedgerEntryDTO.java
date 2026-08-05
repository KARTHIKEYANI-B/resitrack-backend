package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One row in the Admin → Payment Management unified transaction ledger.
 *
 * Combines, read-only, three existing record types into a single date-sorted
 * list — it introduces no new data and changes no existing table:
 *
 *   INCOME  — owner monthly maintenance payments  (Payment, status = PAID)
 *   INCOME  — maintenance batch payments          (BatchPayment, status = PAID)
 *   EXPENSE — expense records                     (Expense)
 *
 * "Other income records" (per the task) has no concrete source in this
 * codebase today — there is no separate Income entity — so this DTO only
 * ever fills from the two PAID-payment sources above plus Expense; serialNo
 * is assigned by the caller after sorting, not stored.
 */
@Data
@Builder
public class TransactionLedgerEntryDTO {
    private int serialNo;
    private LocalDate date;
    private String type;            // "INCOME" | "EXPENSE"
    private String category;        // e.g. "Monthly Maintenance", "Maintenance Batch: <title>", expense category
    private String description;
    private String residentName;    // null for expenses / not applicable
    private String flatNumber;      // null for expenses / not applicable
    private BigDecimal amount;
    private String paymentMethod;
    private String sourceType;      // "MAINTENANCE_PAYMENT" | "BATCH_PAYMENT" | "EXPENSE" — for traceability only
    private Long sourceId;          // id of the underlying Payment / BatchPayment / Expense row

    // Multi-month "Add Payment" submissions (sourceType = MAINTENANCE_PAYMENT
    // only — see Payment.paymentBatchId) collapse to one ledger row covering
    // every selected month, instead of one row per month. Null/empty for an
    // ordinary single-month entry and always null for BATCH_PAYMENT/EXPENSE
    // rows (unrelated to this feature).
    private String paymentBatchId;
    private List<PaymentResponseDTO.MonthLine> monthBreakdown;
    private BigDecimal batchTotalAmount;
}