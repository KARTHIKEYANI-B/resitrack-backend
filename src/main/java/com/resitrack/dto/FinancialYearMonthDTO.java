package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One column of the Financial-Year month selector shown when an Owner /
 * Family Member pays maintenance (CurrentMaintenance → "Pay Maintenance").
 *
 * status is one of:
 *   PAID               — fully settled, cannot be selected again
 *   PARTIALLY_PAID     — selectable; remainingDue is the only payable amount
 *   UNPAID             — selectable; remainingDue == dueAmount
 *   PENDING_VERIFICATION — a submission already exists for this month and is
 *                          awaiting admin action; not selectable (prevents
 *                          duplicate/overlapping submissions for one month)
 *   NOT_DUE            — a future month within the FY that hasn't billed
 *                          yet; not selectable
 */
@Data
@Builder
public class FinancialYearMonthDTO {
    private String     month;         // "2026-05"
    private String     monthLabel;    // "May 2026"
    private BigDecimal dueAmount;     // full monthly maintenance amount
    private BigDecimal paidSoFar;     // already verified/PAID for this month
    private BigDecimal remainingDue;  // dueAmount - paidSoFar, floored at 0
    private String      status;
    private boolean      selectable;
}
