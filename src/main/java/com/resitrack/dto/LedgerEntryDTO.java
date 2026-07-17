package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row on an Owner / Family Member "Ledger Account" — matches the
 * attached Ledger reference format's columns exactly:
 *
 *   Date | Particulars | Vch Type | Vch No. | Debit | Credit
 *
 * Built read-only from existing Payment (billing + collection) and
 * Receipt records — introduces no new source-of-truth data.
 */
@Data
@Builder
public class LedgerEntryDTO {
    private LocalDate date;
    private String particulars;   // e.g. "To Maintanance charges" / "By Indian Overseas Bank"
    private String narration;     // "To" | "By" prefix, kept separate for UI styling
    private String vchType;       // "Sales" (billing) | "Receipt" (payment) | "Opening Balance" | "Closing Balance"
    private String vchNo;
    private BigDecimal debit;
    private BigDecimal credit;
}
