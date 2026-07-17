package com.resitrack.service;

import com.resitrack.dto.LedgerEntryDTO;
import com.resitrack.entity.Payment;
import com.resitrack.entity.Receipt;
import com.resitrack.entity.Resident;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ReceiptRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Owner / Family Member "Ledger Account" — matches the attached Ledger
 * reference format (Date | Particulars | Vch Type | Vch No. | Debit |
 * Credit, with an Opening Balance carried in and a Closing Balance carried
 * out for the selected period).
 *
 * Uses existing payment history only (no new source-of-truth data):
 *   - Every Payment row is a billed month ("To Maintanance charges" —
 *     Vch Type "Sales", debit = amount + lateFee), dated the 1st of its
 *     billing month (paymentMonth).
 *   - Every PAID Payment additionally contributes a matching credit entry
 *     ("By <payment mode>" — Vch Type "Receipt"), dated its paymentDate,
 *     with the Vch No. taken from the linked Receipt when one exists.
 *
 * Balancing follows the standard ledger convention used by the reference
 * export: the period's Opening/Closing Balance is placed on whichever side
 * (Debit = "To", Credit = "By") balances the two columns, and carries
 * forward as next period's Opening Balance.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final PaymentRepository  paymentRepo;
    private final ReceiptRepository  receiptRepo;
    private final ResidentRepository residentRepo;

    private record RawEntry(LocalDate date, String particulars, String narration,
                             String vchType, String vchNo, BigDecimal debit, BigDecimal credit) {}

    /**
     * @param residentId the OWNER resident id (family members resolve to their owner first)
     * @param year       calendar year the period falls in (required)
     * @param month      1-12 for a single calendar month, or null for the
     *                   full financial year (Apr {year} → Mar {year + 1})
     */
    public Map<String, Object> getResidentLedger(Long residentId, int year, Integer month) {
        Resident resident = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        LocalDate periodStart;
        LocalDate periodEnd;
        if (month != null && month >= 1 && month <= 12) {
            YearMonth ym = YearMonth.of(year, month);
            periodStart = ym.atDay(1);
            periodEnd   = ym.atEndOfMonth();
        } else {
            periodStart = LocalDate.of(year, 4, 1);
            periodEnd   = LocalDate.of(year + 1, 3, 31);
        }
        String periodLabel = fmt(periodStart) + " to " + fmt(periodEnd);

        // ── Build the full, all-time chronological entry list ──────────
        List<Payment> payments = paymentRepo.findByResidentId(residentId);
        List<RawEntry> all = new ArrayList<>();

        for (Payment p : payments) {
            BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            BigDecimal late   = p.getLateFeeAmount() != null ? p.getLateFeeAmount() : BigDecimal.ZERO;
            BigDecimal total  = amount.add(late);

            LocalDate billDate = billingMonthStart(p);
            if (billDate != null) {
                all.add(new RawEntry(billDate, "Maintanance charges", "To",
                        "Sales", String.valueOf(p.getId()), total, BigDecimal.ZERO));
            }

            if (p.getPaymentStatus() == Payment.PaymentStatus.PAID && p.getPaymentDate() != null) {
                String mode = p.getPaymentMethod() != null && !p.getPaymentMethod().isBlank()
                        ? p.getPaymentMethod() : "Payment";
                String vchNo = receiptRepo.findByPaymentId(p.getId())
                        .map(Receipt::getReceiptNumber)
                        .orElse(String.valueOf(p.getId()));
                all.add(new RawEntry(p.getPaymentDate(), mode, "By",
                        "Receipt", vchNo, BigDecimal.ZERO, total));
            }
        }

        all.sort(Comparator.comparing(RawEntry::date)
                .thenComparing(e -> "To".equals(e.narration()) ? 0 : 1));

        // ── Opening balance: net of everything strictly before periodStart ──
        BigDecimal openingBalance = BigDecimal.ZERO;
        List<RawEntry> periodRaw = new ArrayList<>();
        for (RawEntry e : all) {
            if (e.date().isBefore(periodStart)) {
                openingBalance = openingBalance.add(e.debit()).subtract(e.credit());
            } else if (!e.date().isAfter(periodEnd)) {
                periodRaw.add(e);
            }
        }

        // ── Assemble the visible rows: Opening Balance, entries, Closing Balance ──
        List<LedgerEntryDTO> rows = new ArrayList<>();
        BigDecimal totalDebit  = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if (openingBalance.signum() > 0) {
            rows.add(LedgerEntryDTO.builder().date(periodStart).particulars("Opening Balance")
                    .narration("To").vchType("").vchNo("").debit(openingBalance).credit(BigDecimal.ZERO).build());
            totalDebit = totalDebit.add(openingBalance);
        } else if (openingBalance.signum() < 0) {
            BigDecimal abs = openingBalance.negate();
            rows.add(LedgerEntryDTO.builder().date(periodStart).particulars("Opening Balance")
                    .narration("By").vchType("").vchNo("").debit(BigDecimal.ZERO).credit(abs).build());
            totalCredit = totalCredit.add(abs);
        }

        for (RawEntry e : periodRaw) {
            rows.add(LedgerEntryDTO.builder()
                    .date(e.date())
                    .particulars(e.particulars())
                    .narration(e.narration())
                    .vchType(e.vchType())
                    .vchNo(e.vchNo())
                    .debit(e.debit())
                    .credit(e.credit())
                    .build());
            totalDebit  = totalDebit.add(e.debit());
            totalCredit = totalCredit.add(e.credit());
        }

        BigDecimal closingBalance = totalDebit.subtract(totalCredit);
        if (closingBalance.signum() > 0) {
            rows.add(LedgerEntryDTO.builder().date(periodEnd).particulars("Closing Balance")
                    .narration("By").vchType("").vchNo("").debit(BigDecimal.ZERO).credit(closingBalance).build());
            totalCredit = totalCredit.add(closingBalance);
        } else if (closingBalance.signum() < 0) {
            BigDecimal abs = closingBalance.negate();
            rows.add(LedgerEntryDTO.builder().date(periodEnd).particulars("Closing Balance")
                    .narration("To").vchType("").vchNo("").debit(abs).credit(BigDecimal.ZERO).build());
            totalDebit = totalDebit.add(abs);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("residentName", resident.getFullName());
        result.put("flatNumber", resident.getFlatNumber());
        result.put("periodLabel", periodLabel);
        result.put("periodStart", periodStart);
        result.put("periodEnd", periodEnd);
        result.put("openingBalance", openingBalance);
        result.put("closingBalance", closingBalance);
        result.put("totalDebit", totalDebit);
        result.put("totalCredit", totalCredit);
        result.put("entries", rows);
        return result;
    }

    /** The 1st of the payment's billing month (paymentMonth, e.g. "2026-05"), falling back to created date. */
    private LocalDate billingMonthStart(Payment p) {
        if (p.getPaymentMonth() != null && p.getPaymentMonth().matches("\\d{4}-\\d{2}")) {
            String[] parts = p.getPaymentMonth().split("-");
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
        }
        if (p.getCreatedAt() != null) return p.getCreatedAt().toLocalDate();
        return null;
    }

    private static String fmt(LocalDate d) {
        return d.format(java.time.format.DateTimeFormatter.ofPattern("d-MMM-yy"));
    }
}
