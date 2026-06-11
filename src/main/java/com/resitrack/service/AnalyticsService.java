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

    public AnalyticsDTO getAnalyticsSummary(int year, int month) {
        boolean monthlyView = month > 0;

        Double revenue  = monthlyView
                ? paymentRepo.sumPaidAmountByYearAndMonth(year, month)
                : paymentRepo.sumPaidAmountByYear(year);
        Double expenses = monthlyView
                ? expenseRepo.sumByYearAndMonth(year, month)
                : expenseRepo.sumByYear(year);
        revenue  = revenue  == null ? 0.0 : revenue;
        expenses = expenses == null ? 0.0 : expenses;

        // Previous period
        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear  = month == 1 ? year - 1 : year;
        Double prevRevenue  = monthlyView
                ? paymentRepo.sumPaidAmountByYearAndMonth(prevYear, prevMonth)
                : paymentRepo.sumPaidAmountByYear(year - 1);
        Double prevExpenses = monthlyView
                ? expenseRepo.sumByYearAndMonth(prevYear, prevMonth)
                : expenseRepo.sumByYear(year - 1);
        prevRevenue  = prevRevenue  == null ? 0.0 : prevRevenue;
        prevExpenses = prevExpenses == null ? 0.0 : prevExpenses;

        double revenueGrowth = prevRevenue  > 0 ? ((revenue  - prevRevenue)  / prevRevenue  * 100) : 0.0;
        double expenseGrowth = prevExpenses > 0 ? ((expenses - prevExpenses) / prevExpenses * 100) : 0.0;
        double bankCollection;
        double cashCollection;

        if (monthlyView) {
            Double bankRaw = paymentRepo.sumBankCollectedByYearAndMonth(year, month);
            Double cashRaw = paymentRepo.sumCashCollectedByYearAndMonth(year, month);
            bankCollection = bankRaw == null ? 0.0 : bankRaw;
            cashCollection = cashRaw == null ? 0.0 : cashRaw;
        } else {
            bankCollection = 0.0;
            cashCollection = 0.0;
            for (int m = 1; m <= 12; m++) {
                Double bankRaw = paymentRepo.sumBankCollectedByYearAndMonth(year, m);
                Double cashRaw = paymentRepo.sumCashCollectedByYearAndMonth(year, m);
                bankCollection += (bankRaw == null ? 0.0 : bankRaw);
                cashCollection += (cashRaw == null ? 0.0 : cashRaw);
            }
        }

        long paidCount = 0;
        if (monthlyView) {
            String monthStr = year + "-" + String.format("%02d", month);
            Long pc = paymentRepo.countDistinctResidentsPaidByPaymentMonth(monthStr);
            paidCount = pc != null ? pc : 0;
        } else {
            for (int m = 1; m <= 12; m++) {
                Long pc = paymentRepo.countPaidByYearAndMonth(year, m);
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