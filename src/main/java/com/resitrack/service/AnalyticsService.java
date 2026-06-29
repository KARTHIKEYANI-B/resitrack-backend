package com.resitrack.service;

import com.resitrack.dto.AnalyticsDTO;
import com.resitrack.dto.MonthlyChartDTO;
import com.resitrack.entity.Expense;
import com.resitrack.entity.Resident;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PaymentRepository     paymentRepo;
    private final ExpenseRepository     expenseRepo;
    private final ResidentRepository    residentRepo;

    /**
     * FIX (Task 1 — cross-module collection total consistency):
     *
     * "revenue" (and its bank/cash split, and paidCount) for a given month
     * MUST equal Admin Dashboard's "Collected Amount" / Maintenance
     * Summary's Flat+Villa paid total / Paid-Unpaid Details' column total
     * for that exact same month.
     *
     * Previously this method summed by YEAR/MONTH(p.paymentDate) — the
     * calendar date a payment was actually collected/verified — instead of
     * p.paymentMonth, the billing month it was paid toward. Those two can
     * legitimately differ for partial/installment payments (e.g. a June
     * bill's remaining balance collected and verified in July), which let
     * this screen's "Total Revenue" silently diverge from the other
     * screens above, all of which already key strictly off p.paymentMonth
     * (sumPaidAmountByPropertyAndPaymentMonth / sumPaidAmountByPaymentMonth).
     *
     * Now every per-month figure below (revenue, bank/cash split, paid
     * count) is read via the paymentMonth-keyed queries
     * (sumPaidAmountByPaymentMonth / sumBankPaidByPaymentMonth /
     * sumCashPaidByPaymentMonth / countDistinctResidentsPaidByPaymentMonth)
     * — the exact same queries Admin Dashboard and Payment Management's
     * tracking-stats already use — so this screen can never disagree with
     * them for the same calendar month again. The Full Year view sums these
     * same paymentMonth-keyed figures across all 12 months instead of
     * switching basis.
     */
    public AnalyticsDTO getAnalyticsSummary(int year, int month) {
        boolean monthlyView = month > 0;

        // NOTE: built with if/else rather than a ternary — mixing a Double-
        // returning branch (paymentRepo.sumPaidAmountByPaymentMonth, which
        // returns null when a month has zero PAID rows) with a double-
        // returning branch (sumPaidAmountByPaymentMonthForYear) in one
        // ternary would force Java to unbox the Double branch, throwing a
        // NullPointerException on a month with no payments yet. Each branch
        // is null-checked independently instead.
        double revenue;
        if (monthlyView) {
            Double raw = paymentRepo.sumPaidAmountByPaymentMonth(monthKey(year, month));
            revenue = raw != null ? raw : 0.0;
        } else {
            revenue = sumPaidAmountByPaymentMonthForYear(year);
        }

        Double expensesRaw = monthlyView
                ? expenseRepo.sumByYearAndMonth(year, month)
                : expenseRepo.sumByYear(year);
        double expenses = expensesRaw != null ? expensesRaw : 0.0;

        // Previous period
        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear  = month == 1 ? year - 1 : year;

        double prevRevenue;
        if (monthlyView) {
            Double raw = paymentRepo.sumPaidAmountByPaymentMonth(monthKey(prevYear, prevMonth));
            prevRevenue = raw != null ? raw : 0.0;
        } else {
            prevRevenue = sumPaidAmountByPaymentMonthForYear(year - 1);
        }

        Double prevExpensesRaw = monthlyView
                ? expenseRepo.sumByYearAndMonth(prevYear, prevMonth)
                : expenseRepo.sumByYear(year - 1);
        double prevExpenses = prevExpensesRaw != null ? prevExpensesRaw : 0.0;

        double revenueGrowth = prevRevenue  > 0 ? ((revenue  - prevRevenue)  / prevRevenue  * 100) : 0.0;
        double expenseGrowth = prevExpenses > 0 ? ((expenses - prevExpenses) / prevExpenses * 100) : 0.0;
        double bankCollection;
        double cashCollection;

        if (monthlyView) {
            Double bankRaw = paymentRepo.sumBankPaidByPaymentMonth(monthKey(year, month));
            Double cashRaw = paymentRepo.sumCashPaidByPaymentMonth(monthKey(year, month));
            bankCollection = bankRaw == null ? 0.0 : bankRaw;
            cashCollection = cashRaw == null ? 0.0 : cashRaw;
        } else {
            bankCollection = 0.0;
            cashCollection = 0.0;
            for (int m = 1; m <= 12; m++) {
                Double bankRaw = paymentRepo.sumBankPaidByPaymentMonth(monthKey(year, m));
                Double cashRaw = paymentRepo.sumCashPaidByPaymentMonth(monthKey(year, m));
                bankCollection += (bankRaw == null ? 0.0 : bankRaw);
                cashCollection += (cashRaw == null ? 0.0 : cashRaw);
            }
        }

        long paidCount = 0;
        if (monthlyView) {
            Long pc = paymentRepo.countDistinctResidentsPaidByPaymentMonth(monthKey(year, month));
            paidCount = pc != null ? pc : 0;
        } else {
            for (int m = 1; m <= 12; m++) {
                Long pc = paymentRepo.countDistinctResidentsPaidByPaymentMonth(monthKey(year, m));
                if (pc != null) paidCount += pc;
            }
        }

        long totalResidents = residentRepo.countAllActiveNonDeleted();
        long occupiedFlats  = totalResidents;
        long unpaidCount    = Math.max(0, occupiedFlats - paidCount);

        double collectionRate = occupiedFlats > 0 ? (paidCount * 100.0 / occupiedFlats) : 0.0;

        double avgPerFlat  = paidCount > 0 ? revenue / paidCount : 0.0;
        double pendingDues = unpaidCount * avgPerFlat;

        List<MonthlyChartDTO>       monthlyChart      = buildYearlyChart(year);
        List<Map<String, Object>>   expenseCategories = buildExpenseBreakdown(year, month);

        return AnalyticsDTO.builder()
                .year(year)
                .month(monthlyView ? month : null)
                .totalRevenue(revenue)
                .totalExpenses(expenses)
                .netBalance(revenue - expenses)
                .pendingDues(Math.max(0.0, pendingDues))
                .collectionRate(Math.round(collectionRate * 10.0) / 10.0)
                .bankCollection(bankCollection)
                .cashCollection(cashCollection)
                .paidCount((int) paidCount)
                .unpaidCount((int) unpaidCount)
                .totalResidents((int) totalResidents)
                .occupiedFlats((int) occupiedFlats)
                .revenueGrowth(Math.round(revenueGrowth * 10.0) / 10.0)
                .expenseGrowth(Math.round(expenseGrowth * 10.0) / 10.0)
                .monthlyChart(monthlyChart)
                .expenseCategories(expenseCategories)
                .build();
    }

    public List<MonthlyChartDTO> buildYearlyChart(int year) {
        String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        List<MonthlyChartDTO> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Double income  = paymentRepo.sumPaidAmountByYearAndMonth(year, m);
            Double expense = expenseRepo.sumByYearAndMonth(year, m);
            income  = income  == null ? 0.0 : income;
            expense = expense == null ? 0.0 : expense;
            result.add(new MonthlyChartDTO(months[m - 1], income, expense, income - expense));
        }
        return result;
    }

    // "YYYY-MM" billing-month key, matching the format stored in Payment.paymentMonth.
    private String monthKey(int year, int month) {
        return year + "-" + String.format("%02d", month);
    }

    // Sums sumPaidAmountByPaymentMonth(...) across all 12 calendar months of the
    // given year — the Full Year equivalent of the single-month paymentMonth-keyed
    // revenue figure used above, so the Full Year view stays on the exact same
    // billing-month basis as the monthly view instead of switching to a
    // paymentDate-based total.
    private double sumPaidAmountByPaymentMonthForYear(int year) {
        double total = 0.0;
        for (int m = 1; m <= 12; m++) {
            Double raw = paymentRepo.sumPaidAmountByPaymentMonth(monthKey(year, m));
            total += raw == null ? 0.0 : raw;
        }
        return total;
    }

    public List<Map<String, Object>> buildExpenseBreakdown(int year, int month) {
        List<Expense> expenseList = month > 0
                ? expenseRepo.findByYearAndMonth(year, month)
                : expenseRepo.findByYearRange(year, 1, 12);

        Map<String, Double> categorySum = new LinkedHashMap<>();
        for (Expense e : expenseList)
            categorySum.merge(e.getCategory(), e.getAmount().doubleValue(), Double::sum);

        double total = categorySum.values().stream().mapToDouble(Double::doubleValue).sum();

        return categorySum.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category",   entry.getKey());
                    item.put("amount",     entry.getValue());
                    item.put("percentage", total > 0
                            ? Math.round(entry.getValue() / total * 1000.0) / 10.0 : 0.0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getPaymentStats(int year, int month) {
        String monthStr    = month > 0 ? year + "-" + String.format("%02d", month) : null;
        long   paidCount   = monthStr != null
                ? (paymentRepo.countDistinctResidentsPaidByPaymentMonth(monthStr) != null
                   ? paymentRepo.countDistinctResidentsPaidByPaymentMonth(monthStr) : 0)
                : 0;
        long totalResidents = residentRepo.countAllActiveNonDeleted();
        long occupiedFlats  = totalResidents;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("paidCount",     paidCount);
        stats.put("unpaidCount",   Math.max(0, occupiedFlats - paidCount));
        stats.put("totalFlats",    totalResidents);
        stats.put("occupiedFlats", occupiedFlats);
        stats.put("collectionRate", occupiedFlats > 0
                ? Math.round(paidCount * 100.0 / occupiedFlats * 10.0) / 10.0 : 0.0);
        return stats;
    }
}