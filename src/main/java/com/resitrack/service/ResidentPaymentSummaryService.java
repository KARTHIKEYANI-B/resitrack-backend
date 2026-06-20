package com.resitrack.service;

import com.resitrack.entity.Payment;
import com.resitrack.entity.Resident;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resident Paid/Unpaid Detail — Financial Year payment matrix.
 *
 * Powers the new "Resident Paid/Unpaid Detail" screen under
 * Admin/Super Admin → Payment Management.
 *
 * DATA SOURCE (per task requirement #1 / #6):
 * This service intentionally does NOT introduce any new payment-status
 * logic, resident-eligibility logic, or amount calculation. It reuses the
 * exact same building blocks already used by Dashboard, Maintenance
 * Summary, Financial Summary, and Payment Management:
 *
 *   - Resident scope:   ResidentRepository.findAllActiveApprovedOwners()
 *                        (same scope used by FinancialReportService.getCollectionMatrix())
 *   - Payment records:  PaymentRepository.findByStatusAndYearRange(PAID, ...)
 *                        (same query used by FinancialReportService.getCollectionMatrix())
 *   - Monthly grouping: payments are grouped by paymentDate's year-month,
 *                        multiple partial PAID payments in the same month
 *                        are summed — identical grouping rule already used
 *                        by getCollectionMatrix().
 *
 * No new totals are independently computed; totals are derived by summing
 * the same per-resident, per-month figures shown in the table, so they can
 * never drift from Financial Summary's "Maint.Val"/collection figures.
 */
@Service
@RequiredArgsConstructor
public class ResidentPaymentSummaryService {

    private final PaymentRepository  paymentRepo;
    private final ResidentRepository residentRepo;

    private static final String[] MONTH_ABBR = {
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    };

    // Financial Year runs April → March. fyStartYear=2025 means Apr 2025 → Mar 2026,
    // matching the existing "Apr–Mar (FY)" preset already used in Financial Summary
    // (AdminFinancialReport.jsx calls getCollectionMatrix(year, startMonth=4, endMonth=3)).
    private static final int FY_START_MONTH = 4;
    private static final int FY_END_MONTH   = 3;

    /**
     * Returns the financial year (April→March) that "today" falls in,
     * expressed as the calendar year in which April falls.
     * E.g. if today is Jan 2026, the current FY started Apr 2025 → fyStartYear = 2025.
     *       if today is Jun 2026, the current FY started Apr 2026 → fyStartYear = 2026.
     */
    public int getCurrentFinancialYearStart() {
        LocalDate now = LocalDate.now();
        return now.getMonthValue() >= FY_START_MONTH ? now.getYear() : now.getYear() - 1;
    }

    /**
     * Builds the Resident Paid/Unpaid Detail matrix for one financial year.
     *
     * @param fyStartYear the calendar year in which the financial year's
     *                    April falls (e.g. 2025 for FY Apr 2025 – Mar 2026)
     */
    public Map<String, Object> getResidentPaymentDetail(int fyStartYear) {

        // ── Same resident scope as Financial Summary's collection matrix ──
        // Only approved + active OWNER residents — no pending, rejected,
        // deleted, or family-member logins (family members pay against the
        // owner's property, already captured in these PAID records).
        List<Resident> residents = residentRepo.findAllActiveApprovedOwners().stream()
                .sorted(Comparator.comparing(r -> r.getFlatNumber() != null ? r.getFlatNumber() : ""))
                .collect(Collectors.toList());

        // ── Same PAID-payment query/grouping as getCollectionMatrix() ────
        // Apr (fyStartYear) → Mar (fyStartYear + 1): a cross-calendar-year
        // range, handled exactly like getCollectionMatrix()'s cross-year branch.
        List<Payment> aprToDecPayments = paymentRepo.findByStatusAndYearRange(
                Payment.PaymentStatus.PAID, fyStartYear, FY_START_MONTH, 12);
        List<Payment> janToMarPayments = paymentRepo.findByStatusAndYearRange(
                Payment.PaymentStatus.PAID, fyStartYear + 1, 1, FY_END_MONTH);

        List<Payment> allPayments = new ArrayList<>(aprToDecPayments.size() + janToMarPayments.size());
        allPayments.addAll(aprToDecPayments);
        allPayments.addAll(janToMarPayments);

        // Group: residentId -> (yyyy-MM -> summed PAID amount).
        // Multiple partial PAID payments in the same month are summed here,
        // identical to getCollectionMatrix()'s BigDecimal::add merge.
        Map<Long, Map<String, BigDecimal>> paymentMap = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getPaymentDate() == null) continue;
            Long   resId    = p.getResident().getId();
            String monthKey = p.getPaymentDate().getYear() + "-"
                    + String.format("%02d", p.getPaymentDate().getMonthValue());
            paymentMap
                .computeIfAbsent(resId, k -> new LinkedHashMap<>())
                .merge(monthKey, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
                       BigDecimal::add);
        }

        // ── Build the 12 FY month columns: Apr..Dec (fyStartYear), Jan..Mar (fyStartYear+1) ──
        List<String> monthKeys   = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();
        for (int m = FY_START_MONTH; m <= 12; m++) {
            monthKeys.add(fyStartYear + "-" + String.format("%02d", m));
            monthLabels.add(MONTH_ABBR[m - 1]);
        }
        for (int m = 1; m <= FY_END_MONTH; m++) {
            monthKeys.add((fyStartYear + 1) + "-" + String.format("%02d", m));
            monthLabels.add(MONTH_ABBR[m - 1]);
        }

        // ── Build rows: one per resident, blank (null) cell for unpaid months ──
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> columnTotals = new LinkedHashMap<>();
        for (String key : monthKeys) columnTotals.put(key, BigDecimal.ZERO);
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Resident r : residents) {
            Map<String, BigDecimal> resPayments = paymentMap.getOrDefault(r.getId(), Map.of());

            BigDecimal rowTotal = BigDecimal.ZERO;
            Map<String, Object> rowMonths = new LinkedHashMap<>();

            for (String key : monthKeys) {
                BigDecimal amt = resPayments.get(key); // null if no PAID payment that month
                if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                    rowMonths.put(key, amt);                 // show actual paid total
                    rowTotal = rowTotal.add(amt);
                    columnTotals.merge(key, amt, BigDecimal::add);
                } else {
                    rowMonths.put(key, null);                 // blank cell — never "Unpaid"/0/N-A text
                }
            }

            grandTotal = grandTotal.add(rowTotal);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("residentId",   r.getId());
            row.put("residentName", r.getFullName());
            row.put("flatNo",       r.getFlatNumber() != null ? r.getFlatNumber() : "—");
            row.put("propertyType", r.getPropertyType() != null ? r.getPropertyType().name() : "FLAT");
            row.put("months",       rowMonths);
            row.put("rowTotal",     rowTotal);
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("financialYearStart", fyStartYear);
        result.put("financialYearEnd",   fyStartYear + 1);
        result.put("financialYearLabel", "FY " + fyStartYear + "-" + String.valueOf(fyStartYear + 1).substring(2));
        result.put("monthKeys",          monthKeys);
        result.put("monthLabels",        monthLabels);
        result.put("rows",               rows);
        result.put("columnTotals",       columnTotals);
        result.put("grandTotal",         grandTotal);
        result.put("totalResidents",     residents.size());
        return result;
    }
}