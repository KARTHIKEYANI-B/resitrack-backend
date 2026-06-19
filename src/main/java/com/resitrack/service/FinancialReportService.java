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

        List<Payment> allPayments = paymentRepo.findByStatusAndYearRange(
                Payment.PaymentStatus.PAID, year, startMonth, endMonth);

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

        double totalExpenses  = safe(expenseRepo.sumByYearRange(year, startMonth, endMonth));
        double totalCollected = grandTotal.doubleValue();
        double prevBalance    = safe(paymentRepo.sumPaidAmountByYearRange(year, 1, startMonth - 1))
                              - safe(expenseRepo.sumByYearBeforeMonth(year, startMonth));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",           year);
        result.put("startMonth",     startMonth);
        result.put("endMonth",       endMonth);
        result.put("monthKeys",      monthKeys);
        result.put("monthLabels",    monthLabels);
        result.put("rows",           rows);
        result.put("columnTotals",   columnTotals);
        result.put("grandTotal",     grandTotal);
        result.put("openingBalance", prevBalance);
        result.put("totalCollected", totalCollected);
        result.put("totalExpenses",  totalExpenses);
        result.put("closingBalance", prevBalance + totalCollected - totalExpenses);
        result.put("pendingDues",    estimatePendingDues(residents, year, endMonth));
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

        List<Expense> expenses;
        if (startMonth == endMonth) {
            expenses = expenseRepo.findByYearAndMonth(year, startMonth);
        } else {
            expenses = expenseRepo.findByYearRange(year, startMonth, endMonth);
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

        double income  = safe(paymentRepo.sumPaidAmountByYearRange(year, startMonth, endMonth));
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

    public Map<String, Object> getFinancialSummary(int year) {

        double totalCollected = safe(paymentRepo.sumPaidAmountByYear(year));
        double totalExpenses  = safe(expenseRepo.sumByYear(year));
        // Active + approved OWNER count — matches the dashboard and resident list
        long   totalResidents = residentRepo.countAllActiveNonDeleted();

        // FIXED: use distinct residents who paid (not raw payment count)
        int    curMonth       = LocalDate.now().getMonthValue();
        String curMonthStr    = year + "-" + String.format("%02d", curMonth);
        long   paidThisMonth  = safe(paymentRepo.countDistinctResidentsPaidByPaymentMonth(curMonthStr));

        double collRate = totalResidents > 0
                ? (paidThisMonth * 100.0 / totalResidents) : 0;

        Map<String, Object> monthlySummary = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            double inc = safe(paymentRepo.sumPaidAmountByYearAndMonth(year, m));
            double exp = safe(expenseRepo.sumByYearAndMonth(year, m));
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("label",   MONTH_ABBR[m - 1]);
            mv.put("income",  inc);
            mv.put("expense", exp);
            mv.put("balance", inc - exp);
            monthlySummary.put(String.format("%02d", m), mv);
        }

        double allTimeBankCollected = safe(paymentRepo.sumAllTimeBankCollected());
        double allTimeCashCollected = safe(paymentRepo.sumAllTimeCashCollected());
        double allTimeBankExpense   = safe(expenseRepo.sumAllTimeBankExpense());
        double allTimeCashExpense   = safe(expenseRepo.sumAllTimeCashExpense());

        double bankBalance = allTimeBankCollected - allTimeBankExpense;
        double cashBalance = allTimeCashCollected - allTimeCashExpense;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",           year);
        result.put("totalCollected", totalCollected);
        result.put("totalExpenses",  totalExpenses);
        result.put("netBalance",     totalCollected - totalExpenses);
        result.put("bankBalance",    bankBalance);
        result.put("cashBalance",    cashBalance);
        result.put("totalResidents", totalResidents);
        result.put("paidThisMonth",  paidThisMonth);
        result.put("collectionRate", Math.round(collRate * 10.0) / 10.0);
        result.put("monthlySummary", monthlySummary.values());
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

    private double estimatePendingDues(List<Resident> residents, int year, int upToMonth) {
        Maintenance activeMaint = maintenanceService.getActiveMaintenanceConfig().orElse(null);
        double total = 0;
        for (Resident r : residents) {
            BigDecimal maint = getMonthlyMaintenance(r, activeMaint);
            if (maint.compareTo(BigDecimal.ZERO) == 0) continue;

            double paid     = safe(paymentRepo.sumPaidByResidentAndYearRange(r.getId(), year, 1, upToMonth));
            double expected = maint.doubleValue() * upToMonth;
            total += Math.max(0, expected - paid);
        }
        return total;
    }
}