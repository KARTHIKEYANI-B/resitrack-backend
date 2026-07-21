package com.resitrack.service;

import com.resitrack.entity.Expense;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Payment;
import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Resident;
import com.resitrack.repository.ExpenseRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.util.NaturalOrderComparator;
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

    private static final String[] FULL_MONTHS = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    public Map<String, Object> getCollectionMatrix(int year, int startMonth, int endMonth) {

        // Only approved + active OWNER residents — no pending, rejected, deleted, or family-member logins
        //
        // FIX: flatNumber is a plain string column ("1", "10", "A-101", ...),
        // so sorting it directly is lexicographic — "10" sorts before "2",
        // and FLAT/VILLA rows interleave in whatever order the string
        // comparison happens to produce, which is what looked "random" here.
        // Grouped by property type first (FLAT block, then VILLA block —
        // same convention MaintenanceService's Flat/Villa owner lists
        // already use), then natural-order by flat/villa number within each
        // group via the same NaturalOrderComparator that screen and
        // ResidentPaymentSummaryService already use, so "9" sorts before
        // "10" and this table's resident order matches those screens'.
        List<Resident> residents = residentRepo.findAllActiveApprovedOwners().stream()
                .sorted(Comparator
                        .comparing((Resident r) -> r.getPropertyType() != null ? r.getPropertyType() : PropertyType.FLAT)
                        .thenComparing(r -> r.getFlatNumber() != null ? r.getFlatNumber() : "",
                                NaturalOrderComparator.INSTANCE))
                .collect(Collectors.toList());

        // ── Fetch PAID payments for the period, grouped by BILLING month ───
        //
        // FIX (Task 1 — cross-module collection total consistency): this
        // table's per-resident, per-month cells (and therefore its column
        // totals and grandTotal) MUST equal Admin Dashboard's "Collected
        // Amount" / Maintenance Summary's paid total / Paid-Unpaid Details'
        // column total for the same month. Previously grouped PAID payments
        // by p.getPaymentDate()'s calendar month — the date a payment was
        // actually collected/verified — instead of p.paymentMonth, the
        // billing month it was paid toward. Those two can legitimately
        // differ for partial/installment payments (e.g. a June bill's
        // remaining balance collected and verified in July), which could
        // split one bill's installments across two different month columns
        // here while every other screen still showed them as one June
        // total — exactly the "different value in Financial Summary" bug.
        //
        // Now fetched via findByStatusAndPaymentMonthRange (the same
        // paymentMonth-keyed range query ResidentPaymentSummaryService
        // already uses for the Paid/Unpaid Detail screen) and grouped by
        // p.getPaymentMonth() directly — paymentMonth is always a
        // zero-padded "YYYY-MM" string, so building the lexicographic range
        // bounds below and comparing them is safe and chronologically
        // correct across a year boundary, exactly like that service's own
        // FY range.
        String startKey = year + "-" + String.format("%02d", startMonth);
        String endKey   = (startMonth <= endMonth ? year : year + 1)
                + "-" + String.format("%02d", endMonth);

        List<Payment> allPayments = paymentRepo.findByStatusAndPaymentMonthRange(
                Payment.PaymentStatus.PAID, startKey, endKey);

        Map<Long, Map<String, BigDecimal>> paymentMap = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getPaymentMonth() == null || p.getResident() == null) continue;
            Long   resId    = p.getResident().getId();
            String monthKey = p.getPaymentMonth();
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

        // ── "Pending Amount" column ──────────────────────────────────────
        // FIX: previously computed via pendingAmountForResident(), which
        // summed the resident's paid amount over the period using
        // paymentRepo.sumPaidByResidentAndYearRange() — a paymentDate-keyed
        // query (YEAR/MONTH(p.paymentDate) BETWEEN ...). Every other figure
        // on this screen (and Maintenance Summary / Paid-Unpaid Details /
        // Dashboard) was already migrated to key off p.paymentMonth (the
        // BILLING month) instead, specifically because a payment's
        // paymentDate — when it was actually collected/verified — can fall
        // in a different calendar month than the month it was billed for
        // (late verification, or a partial installment collected the
        // following month). That mismatch let Pending Amount silently
        // disagree with every other screen and with this table's own month
        // columns, producing the "incorrect/random" values reported.
        //
        // Now computed per month, directly from resPayments (the same
        // paymentMonth-keyed map already built above for this row's own
        // month cells — see the "PAID payments ... grouped by BILLING
        // month" block near the top of this method), as:
        //   pending = Σ over months in [monthKeys] of max(0, maintenance − paidThatMonth)
        // i.e. each month's shortfall only (a fully-paid month contributes
        // 0; a partially-paid month contributes its remaining balance,
        // never a negative "credit" that could offset another month's
        // due). This is the same rule illustrated in the task spec:
        //   Apr paid, May paid ₹400 of ₹1000, Jun unpaid
        //     → pending = (1000-1000) + (1000-400) + (1000-0) = ₹1600
        // and it reconciles by construction with this table's own "months"
        // cells, Maintenance Summary, Paid/Unpaid Details, and Dashboard,
        // since they all read from the same paymentMonth-keyed payment
        // data for the same period.
        double totalPendingDues = 0;

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

            BigDecimal maintVal = getMonthlyMaintenance(r, activeMaint);
            double residentPending = pendingAmountForResident(maintVal, resPayments, monthKeys);
            totalPendingDues += residentPending;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("residentId",   r.getId());
            row.put("flatNo",       r.getFlatNumber() != null ? r.getFlatNumber() : "—");
            row.put("ownerName",    r.getFullName());
            row.put("sqFt",         r.getSqFt() != null ? r.getSqFt() : 0);
            row.put("propertyType", r.getPropertyType() != null ? r.getPropertyType().name() : "FLAT");
            row.put("maintValue",   maintVal);
            row.put("months",       rowMonths);
            row.put("total",        rowTotal);
            row.put("pendingAmount", residentPending);
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
        // FIX (Task 1): opening balance's collected portion must use the same
        // paymentMonth basis as the matrix above it, not paymentDate — see
        // sumPaidAmountByPaymentMonthRange() for the shared reasoning.
        // (sumPaidAmountByPaymentMonthRange already returns a null-safe
        // primitive double — no extra safe() wrapper needed here.)
        double prevBalance    = sumPaidAmountByPaymentMonthRange(year, 1, startMonth - 1)
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
        // Same figure as Σ rows[*].pendingAmount — computed in the loop
        // above from the same per-month, paymentMonth-keyed data, so this
        // aggregate can never drift from the column it's the sum of.
        result.put("pendingDues",    totalPendingDues);
        result.put("totalResidents", residents.size());
        return result;
    }

    /**
     * Drill-down detail for one cell of getCollectionMatrix() above — every
     * individual PAID payment whose BILLING month is `year-month`. Uses the
     * same paymentMonth-keyed lookup as the matrix (findByStatusAndPaymentMonthRange
     * with start == end) so this detail view's total always reconciles with
     * the matrix cell that links to it, instead of the previous
     * paymentDate-based lookup which could disagree for installments
     * collected/verified in a different calendar month than their billing
     * month.
     */
    public Map<String, Object> getMonthlyDetail(int year, int month) {

        String monthKey = year + "-" + String.format("%02d", month);
        List<Payment> payments = paymentRepo.findByStatusAndPaymentMonthRange(
                Payment.PaymentStatus.PAID, monthKey, monthKey);

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

        // FIX (Task 1 — cross-module collection total consistency): "income"
        // here must use the same paymentMonth basis as Admin Dashboard /
        // Maintenance Summary / Paid-Unpaid Details / Financial Summary —
        // see sumPaidAmountByPaymentMonthRange() for the shared reasoning.
        // (It already returns a null-safe primitive double — no extra
        // safe() wrapper needed.)
        double income = sumPaidAmountByPaymentMonthRange(year, startMonth, endMonth);
        double prevBal = sumPaidAmountByPaymentMonthRange(year, 1, startMonth - 1)
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
    // IMPORTANT — NO OTHER CALCULATION LOGIC WAS CHANGED beyond the Task 1
    // paymentMonth-basis fix documented at the loop below:
    //   This method does not introduce any new SQL or aggregation rule
    //   beyond switching the per-month income lookup from paymentDate to
    //   paymentMonth (see the loop below). It only changes *which* 12
    //   (calendar-year, month) pairs are summed and in what order they're
    //   presented; the underlying queries are:
    //     - paymentRepo.sumPaidAmountByPaymentMonth(y + "-" + m)
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

        // FIX (Task 1 — cross-module collection total consistency):
        // "income" per month MUST equal Admin Dashboard's "Collected Amount" /
        // Maintenance Summary's paid total / Paid-Unpaid Details' column
        // total for that exact same month. Previously summed by
        // YEAR/MONTH(p.paymentDate) (sumPaidAmountByYearAndMonth) — the
        // calendar date a payment was actually collected/verified — instead
        // of p.paymentMonth, the billing month it was paid toward, which let
        // this exact screen ("Financial Summary") silently diverge from the
        // others for installments collected/verified in a different
        // calendar month than their billing month. Now keyed by
        // paymentMonth via sumPaidAmountByPaymentMonth, same as Dashboard /
        // Payment Management tracking-stats / Analytics Summary.
        for (int[] ym : fyMonths) {
            int y = ym[0], m = ym[1];
            double inc = safe(paymentRepo.sumPaidAmountByPaymentMonth(y + "-" + String.format("%02d", m)));
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

    /**
     * Yearly Financial Summary — Requirement #4 (Admin / Super Admin).
     *
     * Reshapes the same month-wise income/expense figures used by
     * {@link #getFinancialSummary(int)} (identical repository queries,
     * so totals always agree with that screen) into the Tally-style
     * "Sales Register — Monthly Summary" layout from the attached
     * reference format: one row per financial-year month with
     * Debit (expenses), Credit (collections) and a running Closing
     * Balance, plus a Grand Total row.
     */
    public Map<String, Object> getYearlySummary(int year) {
        List<int[]> fyMonths = new ArrayList<>();
        for (int m = 4; m <= 12; m++) fyMonths.add(new int[]{ year,     m });
        for (int m = 1; m <= 3;  m++) fyMonths.add(new int[]{ year + 1, m });

        List<Map<String, Object>> rows = new ArrayList<>();
        double running = 0.0, totalDebit = 0.0, totalCredit = 0.0;

        for (int[] ym : fyMonths) {
            int y = ym[0], m = ym[1];
            double income  = safe(paymentRepo.sumPaidAmountByPaymentMonth(y + "-" + String.format("%02d", m)));
            double expense = safe(expenseRepo.sumByYearAndMonth(y, m));
            running     += income - expense;
            totalDebit  += expense;
            totalCredit += income;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month",         FULL_MONTHS[m - 1]);
            row.put("debit",         expense);
            row.put("credit",        income);
            row.put("hasActivity",   income != 0.0 || expense != 0.0);
            row.put("closingBalance", Math.abs(running));
            row.put("balanceType",   running >= 0 ? "Cr" : "Dr");
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",               year);
        result.put("financialYearLabel", "FY " + year + "-" + String.valueOf(year + 1).substring(2));
        result.put("periodStart",        year + "-04-01");
        result.put("periodEnd",          (year + 1) + "-03-31");
        result.put("rows",               rows);
        result.put("totalDebit",         totalDebit);
        result.put("totalCredit",        totalCredit);
        result.put("closingBalance",     Math.abs(running));
        result.put("balanceType",        running >= 0 ? "Cr" : "Dr");
        return result;
    }

    /**
     * FIX (Task 1 — cross-module collection total consistency):
     *
     * "totalCollection" for a given month MUST equal Admin Dashboard's
     * "Collected Amount" / Maintenance Summary's Flat+Villa paid total /
     * Paid-Unpaid Details' column total for that exact same month — this
     * is the User-facing "Financial Summary for [month]" screen
     * (UserFinancialReport.jsx → /user/financial-report/monthly-summary).
     *
     * Previously summed by YEAR/MONTH(p.paymentDate) — the calendar date a
     * payment was actually collected/verified — instead of p.paymentMonth,
     * the billing month it was paid toward. Those two can legitimately
     * differ for partial/installment payments (e.g. a June bill's
     * remaining balance collected and verified in July), which let this
     * screen's totals silently diverge from the screens above. Now keyed
     * by paymentMonth via sumPaidAmountByPaymentMonth /
     * sumBankPaidByPaymentMonth / sumCashPaidByPaymentMonth — the same
     * queries Admin Dashboard and Payment Management already use.
     */
    public Map<String, Object> getMonthlySummary(int year, int month) {
        String paymentMonthKey = year + "-" + String.format("%02d", month);
        double totalCollection = safe(paymentRepo.sumPaidAmountByPaymentMonth(paymentMonthKey));
        double bankCollection  = safe(paymentRepo.sumBankPaidByPaymentMonth(paymentMonthKey));
        double cashCollection  = safe(paymentRepo.sumCashPaidByPaymentMonth(paymentMonthKey));

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

    /**
     * Sums sumPaidAmountByPaymentMonth(...) — the same paymentMonth-keyed,
     * PAID-only query Admin Dashboard / Maintenance Summary / Paid-Unpaid
     * Details / Payment Management already use — across a startMonth..endMonth
     * range of the given calendar year, the paymentMonth equivalent of the
     * old paymentDate-based sumPaidAmountByYearRange.
     *
     * Used everywhere this file previously summed a month range "by
     * collection date" (income figures, opening/closing balances): grouping
     * by paymentDate instead of paymentMonth could split one bill's
     * installments across two different calendar months if a remaining
     * balance was collected/verified in a later month than it was billed
     * for, silently diverging from every other screen's totals for the
     * same billing month — the exact "different value in Financial Summary"
     * bug this fixes.
     *
     * @param endMonth pass 0 to mean "no months at all" (the degenerate
     *                 "before the period starts" case some callers use,
     *                 e.g. startMonth=1, endMonth=0 → nothing before
     *                 January). Any other startMonth > endMonth combination
     *                 is treated as a genuine cross-year Financial-Year
     *                 range (e.g. startMonth=4, endMonth=3 → Apr(year)..Mar(year+1)),
     *                 exactly like getCollectionMatrix()/getExpenseReport()
     *                 already interpret it — endMonth=0 is the only
     *                 sentinel value, not "endMonth == startMonth - 1" in
     *                 general, since that expression is also true for a
     *                 real one-year-long FY range such as (4, 3).
     */
    private double sumPaidAmountByPaymentMonthRange(int year, int startMonth, int endMonth) {
        if (endMonth < 1) return 0.0; // sentinel: no months in range at all

        double total = 0.0;
        if (startMonth <= endMonth) {
            for (int m = startMonth; m <= endMonth; m++) {
                total += safe(paymentRepo.sumPaidAmountByPaymentMonth(
                        year + "-" + String.format("%02d", m)));
            }
        } else {
            for (int m = startMonth; m <= 12; m++) {
                total += safe(paymentRepo.sumPaidAmountByPaymentMonth(
                        year + "-" + String.format("%02d", m)));
            }
            for (int m = 1; m <= endMonth; m++) {
                total += safe(paymentRepo.sumPaidAmountByPaymentMonth(
                        (year + 1) + "-" + String.format("%02d", m)));
            }
        }
        return total;
    }

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

    // ── "Pending Amount" (per resident, per month, capped) ──────────────────
    //
    // FIX: replaces the old estimatePendingDues()/pendingAmountForResident()
    // pair, which both computed a single whole-period
    // (maintenance × numberOfMonths) − (paymentDate-ranged paid total) and
    // could go negative-then-clamp in a way that let an overpayment in one
    // month mask a shortfall in another, and which relied on the
    // paymentDate-keyed sumPaidByResidentAndYearRange() query — inconsistent
    // with every other screen's paymentMonth basis (see the comment above
    // this method's call site in getCollectionMatrix()).
    //
    // Now computed per month from `resPayments` (paymentMonth-keyed, the
    // same map used for this row's own month cells):
    //   pending = Σ over monthKeys of max(0, maintenance − paidThatMonth)
    // A fully-paid month contributes 0 ("ignore fully paid months"); a
    // partially-paid month contributes only its remaining balance; an
    // unpaid month contributes the full maintenance amount. Verified
    // (paymentStatus = PAID) payments only, matching `resPayments`.
    private double pendingAmountForResident(BigDecimal maint, Map<String, BigDecimal> resPayments,
                                             List<String> monthKeys) {
        if (maint == null || maint.compareTo(BigDecimal.ZERO) <= 0) return 0;

        double total = 0;
        for (String key : monthKeys) {
            BigDecimal paidForMonth = resPayments.getOrDefault(key, BigDecimal.ZERO);
            total += Math.max(0, maint.doubleValue() - paidForMonth.doubleValue());
        }
        return total;
    }
}