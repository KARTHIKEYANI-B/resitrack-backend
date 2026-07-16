package com.resitrack.service;

import com.resitrack.entity.Payment;
import com.resitrack.entity.Resident;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.util.NaturalOrderComparator;
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
 * logic or resident-eligibility logic. It reuses the same building block
 * already used by Dashboard, Maintenance Summary, Financial Summary, and
 * Payment Management for resident scope:
 *
 *   - Resident scope:   ResidentRepository.findAllActiveApprovedOwners()
 *                        (same scope used by FinancialReportService.getCollectionMatrix())
 *
 * MONTHLY GROUPING — fixed to group by BILLING month, not collection date:
 *
 *   Each column on this screen represents a billing month (the month a
 *   resident owed maintenance for), so every payment must be grouped by
 *   p.paymentMonth (e.g. "2026-05") — the stable field set once when a
 *   payment is first submitted/recorded and carried through to its PAID
 *   Payment row regardless of when it's actually verified.
 *
 *   p.paymentDate (when the money was actually collected/verified) is a
 *   DIFFERENT concept and can legitimately differ between two partial
 *   installments of the very same bill — e.g. ₹3040 collected and verified
 *   on the 2nd of the month, then a ₹2 remaining-balance top-up collected
 *   and verified a few days later. Both share paymentMonth="2026-05", but
 *   grouping by paymentDate's calendar month could split them into
 *   different cells (or even different columns) if verification happened
 *   to land in different calendar months, making the column show only the
 *   most recently-collected installment instead of the bill's total.
 *   Financial Summary's own collection ledger intentionally still groups
 *   by paymentDate (that report tracks cash flow timing, a different
 *   question) — this screen answers "how much has this owner paid toward
 *   each month's bill," which paymentMonth answers directly and
 *   paymentDate does not. Financial Summary itself is unchanged.
 *
 *   Multiple PAID payments sharing one paymentMonth are summed (BigDecimal
 *   add), supporting any number of partial/installment payments.
 *
 * No new totals are independently computed beyond this fix; row totals and
 * column totals are still derived purely by summing the same per-resident,
 * per-month figures shown in the table.
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
        // Natural-order sort — villa numbers (1-41) are numerically below
        // flat numbers (42-104), so ascending flat/villa-number order
        // naturally groups villas first, then flats. A plain String sort
        // would instead go lexicographic ("10" before "2"), scattering rows.
        List<Resident> residents = residentRepo.findAllActiveApprovedOwners().stream()
                .sorted(Comparator.comparing(
                        r -> r.getFlatNumber() != null ? r.getFlatNumber() : "",
                        NaturalOrderComparator.INSTANCE))
                .collect(Collectors.toList());

        // ── Build the 12 FY month columns first: Apr..Dec (fyStartYear), Jan..Mar (fyStartYear+1) ──
        // Built before fetching payments because the fetch range below is
        // expressed directly in terms of these same billing-month keys.
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
        Set<String> monthKeySet = new HashSet<>(monthKeys);

        // ── Fetch every PAID payment whose BILLING month falls in this FY ──
        // monthKeys is already ordered Apr(fyStartYear) .. Mar(fyStartYear+1),
        // so the first and last entries are exactly the lexicographic bounds
        // a string BETWEEN needs ("2026-04" ... "2027-03").
        List<Payment> allPayments = paymentRepo.findByStatusAndPaymentMonthRange(
                Payment.PaymentStatus.PAID, monthKeys.get(0), monthKeys.get(monthKeys.size() - 1));

        // Group: residentId -> (billing month "yyyy-MM" -> summed PAID amount).
        // Multiple partial/installment PAID payments sharing the same
        // paymentMonth are summed here via BigDecimal::add — this is the fix:
        // grouping by the stable billing month (not the collection date)
        // guarantees every installment of one bill lands in the same cell.
        Map<Long, Map<String, BigDecimal>> paymentMap = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getResident() == null) continue; // defensive — should never happen for a real payment row
            String monthKey = p.getPaymentMonth();
            if (monthKey == null || !monthKeySet.contains(monthKey)) continue; // defensive bound check
            Long resId = p.getResident().getId();
            paymentMap
                .computeIfAbsent(resId, k -> new LinkedHashMap<>())
                .merge(monthKey, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
                       BigDecimal::add);
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
                BigDecimal amt = resPayments.get(key); // null if no PAID payment for that billing month
                if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                    rowMonths.put(key, amt);                 // show actual cumulative paid total
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