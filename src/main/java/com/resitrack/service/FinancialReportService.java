package com.resitrack.service;

import com.resitrack.entity.Expense;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Payment;
import com.resitrack.entity.Resident;
import com.resitrack.repository.ExpenseRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final PaymentRepository  paymentRepo;
    private final ExpenseRepository  expenseRepo;
    private final ResidentRepository residentRepo;
    private final MaintenanceService maintenanceService;

    private static final String[] MONTH_ABBR = {
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    };

    public Map<String, Object> getCollectionMatrix(int year, int startMonth, int endMonth) {

        // Only approved + active OWNER residents — no pending, rejected, deleted, or family-member logins
        List<Resident> residents = residentRepo.findAllActiveApprovedOwners().stream()
                .sorted(Comparator.comparing(r -> r.getFlatNumber() != null ? r.getFlatNumber() : ""))
                .collect(Collectors.toList());

        // ── Fetch PAID payments for the period ─────────────────────────────
        //
        // findByStatusAndYearRange(status, year, startMonth, endMonth) runs
        // "YEAR(date) = :year AND MONTH(date) BETWEEN :startMonth AND :endMonth".
        // That SQL BETWEEN is only valid within a single calendar year — if
        // startMonth > endMonth (a Financial Year range such as Apr→Mar,
        // e.g. startMonth=4, endMonth=3), "MONTH BETWEEN 4 AND 3" matches
        // zero rows, since 4..3 is an empty range. The query itself was never
        // changed; this just calls it twice with valid same-year sub-ranges
        // for FY periods — the exact pattern already used by
        // ResidentPaymentSummaryService.getResidentPaymentDetail() for the
        // same Apr-Mar FY range.
        List<Payment> allPayments;
        if (startMonth <= endMonth) {
            allPayments = paymentRepo.findByStatusAndYearRange(
                    Payment.PaymentStatus.PAID, year, startMonth, endMonth);
        } else {
            List<Payment> firstHalf  = paymentRepo.findByStatusAndYearRange(
                    Payment.PaymentStatus.PAID, year, startMonth, 12);
            List<Payment> secondHalf = paymentRepo.findByStatusAndYearRange(
                    Payment.PaymentStatus.PAID, year + 1, 1, endMonth);
            allPayments = new ArrayList<>(firstHalf.size() + secondHalf.size());
            allPayments.addAll(firstHalf);
            allPayments.addAll(secondHalf);
        }

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

        List<String> monthKeys   = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();

        if (startMonth <= endMonth) {
            // Same-calendar-year range (e.g. Jan→Dec, Apr→Jun)
            for (int m = startMonth; m <= endMonth; m++) {
                monthKeys.add(year + "-" + String.format("%02d", m));
                monthLabels.add(MONTH_ABBR[m - 1] + " " + String.valueOf(year).substring(2));
            }
        } else {
            // Cross-year range (e.g. Apr current year → Mar next year)
            for (int m = startMonth; m <= 12; m++) {
                monthKeys.add(year + "-" + String.format("%02d", m));
                monthLabels.add(MONTH_ABBR[m - 1] + " " + String.valueOf(year).substring(2));
            }
            int nextYear = year + 1;
            for (int m = 1; m <= endMonth; m++) {
                monthKeys.add(nextYear + "-" + String.format("%02d", m));
                monthLabels.add(MONTH_ABBR[m - 1] + " " + String.valueOf(nextYear).substring(2));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> columnTotals = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        // Fetch the active maintenance config once — same row used by the
        // Maintenance Summary screen — instead of querying it per resident.
        Maintenance activeMaint = maintenanceService.getActiveMaintenanceConfig().orElse(null);

        for (Resident r : residents) {
            Map<String, BigDecimal> resPayments = paymentMap.getOrDefault(r.getId(), Map.of());

            BigDecimal rowTotal = BigDecimal.ZERO;
            Map<String, Object> rowMonths = new LinkedHashMap<>();

            for (String key : monthKeys) {
                BigDecimal amt = resPayments.getOrDefault(key, BigDecimal.ZERO);
                rowMonths.put(key, amt);
                rowTotal = rowTotal.add(amt);
                columnTotals.merge(key, amt, BigDecimal::add);
            }

            grandTotal = grandTotal.add(rowTotal);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("residentId",   r.getId());
            row.put("flatNo",       r.getFlatNumber() != null ? r.getFlatNumber() : "—");
            row.put("ownerName",    r.getFullName());
            row.put("sqFt",         r.getSqFt() != null ? r.getSqFt() : 0);
            row.put("propertyType", r.getPropertyType() != null ? r.getPropertyType().name() : "FLAT");
            row.put("maintValue",   getMonthlyMaintenance(r, activeMaint));
            row.put("months",       rowMonths);
            row.put("total",        rowTotal);
            rows.add(row);
        }

        // ── Total expenses for the period ──────────────────────────────────
        // sumByYearRange(year, startMonth, endMonth) has the same single-year
        // BETWEEN limitation described above. Same fix: split into two
        // same-year calls for FY (cross-year) ranges; untouched for normal
        // same-year ranges.
        double totalExpenses;
        if (startMonth <= endMonth) {
            totalExpenses = safe(expenseRepo.sumByYearRange(year, startMonth, endMonth));
        } else {
            totalExpenses = safe(expenseRepo.sumByYearRange(year, startMonth, 12))
                          + safe(expenseRepo.sumByYearRange(year + 1, 1, endMonth));
        }

        double totalCollected = grandTotal.doubleValue();
        double prevBalance    = safe(paymentRepo.sumPaidAmountByYearRange(year, 1, startMonth - 1))
                              - safe(expenseRepo.sumByYearBeforeMonth(year, startMonth));

        // FY label for the Collection Statement heading, e.g. "FY 2025-26",
        // mirroring the same label format already used by Financial Summary
        // (getFinancialSummary) and the Resident Paid/Unpaid Detail screen
        // (ResidentPaymentSummaryService). Calendar-year/quarter ranges keep
        // their plain year — only genuinely cross-year (FY) ranges get the
        // "FY yyyy-yy" label.
        boolean isFinancialYear = startMonth > endMonth;
        String periodLabel = isFinancialYear
                ? "FY " + year + "-" + String.valueOf(year + 1).substring(2)
                : String.valueOf(year);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",           year);
        result.put("startMonth",     startMonth);
        result.put("endMonth",       endMonth);
        result.put("isFinancialYear", isFinancialYear);
        result.put("periodLabel",    periodLabel);
        result.put("monthKeys",      monthKeys);
        result.put("monthLabels",    monthLabels);
        result.put("rows",           rows);
        result.put("columnTotals",   columnTotals);
        result.put("grandTotal",     grandTotal);
        result.put("openingBalance", prevBalance);
        result.put("totalCollected", totalCollected);
        result.put("totalExpenses",  totalExpenses);
        result.put("closingBalance", prevBalance + totalCollected - totalExpenses);
        result.put("pendingDues",    estimatePendingDues(residents, year, startMonth, endMonth));
        result.put("totalResidents", residents.size());
        return result;
    }

    public Map<String, Object> getMonthlyDetail(int year, int month) {

        List<Payment> payments = paymentRepo.findByYearAndMonthAndStatus(
                year, month, Payment.PaymentStatus.PAID);

        payments.sort(Comparator.comparing(p ->
                p.getPaymentDate() != null ? p.getPaymentDate() : LocalDate.MIN));

        List<Map<String, Object>> records = new ArrayList<>();
        int slNo = 1;
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (Payment p : payments) {
            Resident r = p.getResident();
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            BigDecimal fee = p.getLateFeeAmount() != null ? p.getLateFeeAmount() : BigDecimal.ZERO;
            runningTotal = runningTotal.add(amt).add(fee);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("slNo",            slNo++);
            rec.put("flatNo",          r.getFlatNumber() != null ? r.getFlatNumber() : "—");
            rec.put("ownerName",       r.getFullName());
            rec.put("transactionId",   p.getTransactionId() != null ? p.getTransactionId() : "—");
            rec.put("paymentDate",     p.getPaymentDate() != null ? p.getPaymentDate().toString() : "—");
            rec.put("amount",          amt);
            rec.put("lateFee",         fee);
            rec.put("totalAmount",     amt.add(fee));
            rec.put("method",          p.getPaymentMethod() != null ? p.getPaymentMethod() : "—");
            rec.put("cumulativeTotal", runningTotal);
            records.add(rec);
        }

        String monthLabel = MONTH_ABBR[month - 1] + " " + year;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",          year);
        result.put("month",         month);
        result.put("monthLabel",    monthLabel);
        result.put("records",       records);
        result.put("totalAmount",   runningTotal);
        result.put("totalPayments", records.size());
        return result;
    }

    public Map<String, Object> getExpenseReport(int year, int startMonth, int endMonth) {

        // findByYearAndMonth/findByYearRange both filter by a single
        // calendar `year`. findByYearRange's "MONTH BETWEEN startMonth AND
        // endMonth" is only valid within one calendar year — for a
        // Financial Year range (startMonth > endMonth, e.g. Apr→Mar) it
        // must be split into two same-year calls, same fix and same reason
        // as getCollectionMatrix() above.
        List<Expense> expenses;
        if (startMonth == endMonth) {
            expenses = expenseRepo.findByYearAndMonth(year, startMonth);
        } else if (startMonth <= endMonth) {
            expenses = expenseRepo.findByYearRange(year, startMonth, endMonth);
        } else {
            List<Expense> firstHalf  = expenseRepo.findByYearRange(year, startMonth, 12);
            List<Expense> secondHalf = expenseRepo.findByYearRange(year + 1, 1, endMonth);
            expenses = new ArrayList<>(firstHalf.size() + secondHalf.size());
            expenses.addAll(firstHalf);
            expenses.addAll(secondHalf);
        }

        expenses.sort(Comparator.comparing(e -> e.getExpenseDate() != null ? e.getExpenseDate() : LocalDate.MIN));

        List<Map<String, Object>> records = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        int slNo = 1;

        for (Expense e : expenses) {
            BigDecimal amt = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
            runningBalance = runningBalance.add(amt);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("slNo",          slNo++);
            rec.put("date",          e.getExpenseDate() != null ? e.getExpenseDate().toString() : "—");
            rec.put("description",   e.getExpenseName());
            rec.put("category",      e.getCategory());
            rec.put("paymentMethod", e.getPaymentMethod() != null ? e.getPaymentMethod() : "Cash");
            rec.put("amount",        amt);
            rec.put("vendorStatus",  e.getVendorStatus() != null ? e.getVendorStatus().name() : "PAID");
            rec.put("remarks",       e.getDescription() != null ? e.getDescription() : "");
            rec.put("runningTotal",  runningBalance);
            records.add(rec);
        }

        Map<String, BigDecimal> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        List<Map<String, Object>> catList = categoryTotals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("amount",   e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        BigDecimal chqTotal  = expenses.stream()
                .filter(e -> !"Cash".equalsIgnoreCase(e.getPaymentMethod()))
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashTotal = runningBalance.subtract(chqTotal);

        double income;
        if (startMonth <= endMonth) {
            income = safe(paymentRepo.sumPaidAmountByYearRange(year, startMonth, endMonth));
        } else {
            income = safe(paymentRepo.sumPaidAmountByYearRange(year, startMonth, 12))
                   + safe(paymentRepo.sumPaidAmountByYearRange(year + 1, 1, endMonth));
        }
        double prevBal = safe(paymentRepo.sumPaidAmountByYearRange(year, 1, startMonth - 1))
                       - safe(expenseRepo.sumByYearBeforeMonth(year, startMonth));

        String periodLabel;
        if (startMonth == endMonth) {
            periodLabel = MONTH_ABBR[startMonth - 1] + " " + year;
        } else if (startMonth <= endMonth) {
            periodLabel = MONTH_ABBR[startMonth - 1] + " – " + MONTH_ABBR[endMonth - 1] + " " + year;
        } else {
            periodLabel = MONTH_ABBR[startMonth - 1] + " " + year
                    + " – " + MONTH_ABBR[endMonth - 1] + " " + (year + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",           year);
        result.put("startMonth",     startMonth);
        result.put("endMonth",       endMonth);
        result.put("periodLabel",    periodLabel);
        result.put("records",        records);
        result.put("categoryTotals", catList);
        result.put("totalCheque",    chqTotal);
        result.put("totalCash",      cashTotal);
        result.put("totalExpenses",  runningBalance);
        result.put("openingBalance", prevBal);
        result.put("totalIncome",    income);
        result.put("bankBalance",    prevBal + income - runningBalance.doubleValue());
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FINANCIAL SUMMARY — Financial Year (Apr → Mar), not Calendar Year
    // ═══════════════════════════════════════════════════════════════════════
    //
    // "year" is the FINANCIAL YEAR START YEAR. e.g. year = 2025 means
    // FY 2025-26 = 1 Apr 2025 → 31 Mar 2026 (the standard Indian financial
    // year convention), matching the "Apr–Mar (FY)" preset already used by
    // the Collection/Expense report tables on this same screen
    // (FinancialReportController / getCollectionMatrix, startMonth=4).
    //
    // IMPORTANT — NO CALCULATION LOGIC WAS CHANGED:
    //   This method does not introduce any new SQL or aggregation rule. It
    //   only changes *which* 12 (calendar-year, month) pairs are summed and
    //   in what order they're presented. Every individual month's figure is
    //   still produced by the exact same, untouched repository calls already
    //   used elsewhere in this class:
    //     - paymentRepo.sumPaidAmountByYearAndMonth(y, m)
    //     - expenseRepo.sumByYearAndMonth(y, m)
    //   For Calendar Year, those 12 calls were y=year, m=1..12.
    //   For Financial Year, they become:
    //     y=year,   m=4..12   (Apr–Dec of the start year)
    //     y=year+1, m=1..3    (Jan–Mar of the following year)
    //   The same cross-year composition pattern is already used by
    //   getCollectionMatrix() above for its "Cross-year range" branch, so
    //   this mirrors an existing, already-correct technique in this file.
    //
    //   totalCollected / totalExpenses / monthlySummary are therefore the
    //   SUM of the same 12 monthly figures a user would see by manually
    //   stepping through Apr–Mar on the Dashboard, Maintenance Summary, or
    //   the Collection/Expense report tables — they cannot diverge.
    public Map<String, Object> getFinancialSummary(int year) {

        // Financial Year: Apr(year) .. Dec(year), then Jan(year+1) .. Mar(year+1)
        List<int[]> fyMonths = new ArrayList<>(); // each entry = {calendarYear, month}
        for (int m = 4; m <= 12; m++) fyMonths.add(new int[]{ year,     m });
        for (int m = 1; m <= 3;  m++) fyMonths.add(new int[]{ year + 1, m });

        double totalCollected = 0.0;
        double totalExpenses  = 0.0;
        Map<String, Object> monthlySummary = new LinkedHashMap<>();

        for (int[] ym : fyMonths) {
            int y = ym[0], m = ym[1];
            double inc = safe(paymentRepo.sumPaidAmountByYearAndMonth(y, m));
            double exp = safe(expenseRepo.sumByYearAndMonth(y, m));
            totalCollected += inc;
            totalExpenses  += exp;

            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("label",   MONTH_ABBR[m - 1] + " " + String.valueOf(y).substring(2));
            mv.put("income",  inc);
            mv.put("expense", exp);
            mv.put("balance", inc - exp);
            // Key by calendar year-month so entries stay uniquely ordered
            // across the Dec(year) → Jan(year+1) boundary.
            monthlySummary.put(y + "-" + String.format("%02d", m), mv);
        }

        // Active + approved OWNER count — matches the dashboard and resident list
        long totalResidents = residentRepo.countAllActiveNonDeleted();

        // "This month" = current calendar month within the FY's start year.
        // UNCHANGED from the original formula — only the surrounding totals
        // were converted from Calendar Year to Financial Year grouping.
        int    curMonth      = LocalDate.now().getMonthValue();
        String curMonthStr   = year + "-" + String.format("%02d", curMonth);
        long   paidThisMonth = safe(paymentRepo.countDistinctResidentsPaidByPaymentMonth(curMonthStr));

        double collRate = totalResidents > 0
                ? (paidThisMonth * 100.0 / totalResidents) : 0;

        double allTimeBankCollected = safe(paymentRepo.sumAllTimeBankCollected());
        double allTimeCashCollected = safe(paymentRepo.sumAllTimeCashCollected());
        double allTimeBankExpense   = safe(expenseRepo.sumAllTimeBankExpense());
        double allTimeCashExpense   = safe(expenseRepo.sumAllTimeCashExpense());

        double bankBalance = allTimeBankCollected - allTimeBankExpense;
        double cashBalance = allTimeCashCollected - allTimeCashExpense;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",            year);
        result.put("financialYear",   true);
        result.put("financialYearLabel", "FY " + year + "-" + String.valueOf(year + 1).substring(2));
        result.put("periodStart",     year + "-04-01");
        result.put("periodEnd",       (year + 1) + "-03-31");
        result.put("totalCollected",  totalCollected);
        result.put("totalExpenses",   totalExpenses);
        result.put("netBalance",      totalCollected - totalExpenses);
        result.put("bankBalance",     bankBalance);
        result.put("cashBalance",     cashBalance);
        result.put("totalResidents",  totalResidents);
        result.put("paidThisMonth",   paidThisMonth);
        result.put("collectionRate",  Math.round(collRate * 10.0) / 10.0);
        result.put("monthlySummary",  monthlySummary.values());
        return result;
    }

    public Map<String, Object> getMonthlySummary(int year, int month) {
        double totalCollection = safe(paymentRepo.sumPaidAmountByYearAndMonth(year, month));
        double bankCollection  = safe(paymentRepo.sumBankCollectedByYearAndMonth(year, month));
        double cashCollection  = safe(paymentRepo.sumCashCollectedByYearAndMonth(year, month));

        double bankExpense  = safe(expenseRepo.sumBankExpenseByYearAndMonth(year, month));
        double cashExpense  = safe(expenseRepo.sumCashExpenseByYearAndMonth(year, month));
        double totalExpense = bankExpense + cashExpense;

        double totalBalance = totalCollection - totalExpense;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",            year);
        result.put("month",           month);
        result.put("monthLabel",      MONTH_ABBR[month - 1] + " " + year);
        result.put("totalCollection", totalCollection);
        result.put("bankCollection",  bankCollection);
        result.put("cashCollection",  cashCollection);
        result.put("totalExpense",    totalExpense);
        result.put("bankExpense",     bankExpense);
        result.put("cashExpense",     cashExpense);
        result.put("totalBalance",    totalBalance);
        return result;
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
    private long   safe(Long   v) { return v != null ? v : 0L; }

    // Maintenance Amount — single source of truth.
    //
    // Previously this hardcoded a stale rate (sqFt × 2.5, HALF_UP rounding),
    // which did not match the Maintenance Summary screen's calculation and
    // caused the "Maint.Val" column in Financial Summary to show incorrect
    // figures.
    //
    // Fixed to delegate to MaintenanceService, which is the authoritative
    // source used by the Maintenance Summary screen (/admin/maintenance/owner-list):
    //   - Uses the currently ACTIVE Maintenance config row
    //     (MaintenanceService.getActiveMaintenanceConfig())
    //   - Applies the exact same formula: ceil(sqFt × ratePerSqFt),
    //     falling back to the configured flat `amount` when ratePerSqFt
    //     or sqFt is unavailable (MaintenanceService.calculateAmountForResident()).
    //
    // Overload accepts a pre-fetched Maintenance config to avoid re-querying
    // the database once per resident when used inside a loop.
    private BigDecimal getMonthlyMaintenance(Resident r, Maintenance activeMaint) {
        if (activeMaint == null) return BigDecimal.ZERO;
        return maintenanceService.calculateAmountForResident(activeMaint, r.getSqFt());
    }

    // ── Estimated Outstanding Dues ──────────────────────────────────────────
    //
    // UNCHANGED FORMULA — only the period the formula is applied to changed:
    //   expected = maintenanceAmount × numberOfMonthsInPeriod
    //   paid     = sum of that resident's PAID payments across the period
    //   pending  = max(0, expected − paid)            (same as before)
    //
    // For a same-calendar-year period (startMonth <= endMonth, e.g. the
    // default Jan→endMonth view), this is byte-for-byte the original
    // calculation: numberOfMonths = endMonth, and the paid lookup is
    // (year, 1, endMonth) — identical to the previous
    // estimatePendingDues(residents, year, upToMonth) behavior.
    //
    // For a Financial Year period (startMonth > endMonth, e.g. Apr→Mar),
    // the paid lookup is split into the same two same-year sub-ranges used
    // above for allPayments/totalExpenses (Apr–Dec of `year`, Jan–endMonth
    // of `year+1`) via the same, unmodified sumPaidByResidentAndYearRange
    // method — so it actually sums the resident's payments across the real
    // 12-month FY window instead of the empty range the old single-call
    // cross-year query would have silently returned.
    private double estimatePendingDues(List<Resident> residents, int year, int startMonth, int endMonth) {
        Maintenance activeMaint = maintenanceService.getActiveMaintenanceConfig().orElse(null);
        boolean isFinancialYear = startMonth > endMonth;
        int numberOfMonths = isFinancialYear
                ? (12 - startMonth + 1) + endMonth   // e.g. Apr→Mar = 9 + 3 = 12
                : endMonth;                          // unchanged: Jan..endMonth

        double total = 0;
        for (Resident r : residents) {
            BigDecimal maint = getMonthlyMaintenance(r, activeMaint);
            if (maint.compareTo(BigDecimal.ZERO) == 0) continue;

            double paid;
            if (isFinancialYear) {
                paid = safe(paymentRepo.sumPaidByResidentAndYearRange(r.getId(), year, startMonth, 12))
                     + safe(paymentRepo.sumPaidByResidentAndYearRange(r.getId(), year + 1, 1, endMonth));
            } else {
                // Unchanged: identical to the original (year, 1, upToMonth) lookup
                paid = safe(paymentRepo.sumPaidByResidentAndYearRange(r.getId(), year, 1, endMonth));
            }

            double expected = maint.doubleValue() * numberOfMonths;
            total += Math.max(0, expected - paid);
        }
        return total;
    }
}