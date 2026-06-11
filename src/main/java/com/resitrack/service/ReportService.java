package com.resitrack.service;

import com.resitrack.entity.Expense;
import com.resitrack.entity.Maintenance;
import com.resitrack.repository.ExpenseRepository;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final PaymentRepository     paymentRepo;
    private final ExpenseRepository     expenseRepo;
    private final ResidentRepository    residentRepo;
    private final MaintenanceRepository maintenanceRepo;

    private static final String[] MONTH_NAMES = {
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    };

    public Map<String, Object> getMonthlyReport(int year, int month) {
        double income   = safe(paymentRepo.sumPaidAmountByYearAndMonth(year, month));
        double expenses = safe(expenseRepo.sumByYearAndMonth(year, month));
        double balance  = income - expenses;

        // Active + approved OWNER count only — excludes pending, rejected, deleted, family members
        long   totalFlats   = residentRepo.countAllActiveNonDeleted();
        Long   paidFlatsRaw = paymentRepo.countPaidByYearAndMonth(year, month);
        long   paidFlats    = paidFlatsRaw != null ? paidFlatsRaw : 0;
        long   pendingFlats = totalFlats - paidFlats;

        double maintAmount = getActiveMaintAmount();
        double pendingDues = pendingFlats * (maintAmount > 0 ? maintAmount : (paidFlats > 0 ? income / paidFlats : 0.0));

        double collectionRate = totalFlats > 0 ? (paidFlats * 100.0 / totalFlats) : 0.0;

        List<Expense> monthExpenses = expenseRepo.findByYearAndMonth(year, month);
        Map<String, Double> categoryTotals = monthExpenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingDouble(e -> e.getAmount().doubleValue())));

        List<Map<String, Object>> expenseBreakdown = categoryTotals.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",  e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("value"), (Double) a.get("value")))
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCollected",    income);
        report.put("totalExpenses",     expenses);
        report.put("balance",           balance);
        report.put("pendingDues",       Math.max(0.0, pendingDues));
        report.put("profitLoss",        balance);
        report.put("collectionRate",    Math.round(collectionRate * 10.0) / 10.0);
        report.put("totalFlats",        totalFlats);
        report.put("paidFlats",         paidFlats);
        report.put("pendingFlats",      pendingFlats);
        report.put("monthlyChart",      buildYearlyChart(year));
        report.put("pendingTrend",      buildPendingTrend(year));
        report.put("expenseBreakdown",  expenseBreakdown);
        return report;
    }

    public Map<String, Object> getQuarterlyReport(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        int endMonth   = startMonth + 2;

        double income = 0, expenses = 0;
        for (int m = startMonth; m <= endMonth; m++) {
            income   += safe(paymentRepo.sumPaidAmountByYearAndMonth(year, m));
            expenses += safe(expenseRepo.sumByYearAndMonth(year, m));
        }

        double balance     = income - expenses;
        // Active + approved OWNER count only
        long   totalFlats  = residentRepo.countAllActiveNonDeleted();
        double maintAmount = getActiveMaintAmount();

        double expectedTotal  = totalFlats * maintAmount * 3;
        double pendingDues    = Math.max(0, expectedTotal - income);
        double collectionRate = expectedTotal > 0
                ? Math.min(100.0, (income / expectedTotal) * 100)
                : 0.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCollected",   income);
        report.put("totalExpenses",    expenses);
        report.put("balance",          balance);
        report.put("pendingDues",      pendingDues);
        report.put("profitLoss",       balance);
        report.put("collectionRate",   Math.round(collectionRate * 10.0) / 10.0);
        report.put("monthlyChart",     buildYearlyChart(year));
        report.put("pendingTrend",     buildPendingTrend(year));
        report.put("expenseBreakdown", buildExpenseBreakdownForYear(year));
        return report;
    }

    public Map<String, Object> getYearlyReport(int year) {
        double income   = safe(paymentRepo.sumPaidAmountByYear(year));
        double expenses = safe(expenseRepo.sumByYear(year));
        double balance  = income - expenses;

        long   totalFlats  = residentRepo.countAllActiveNonDeleted();
        double maintAmount = getActiveMaintAmount();

        double expectedTotal  = totalFlats * maintAmount * 12;
        double pendingDues    = Math.max(0, expectedTotal - income);
        double collectionRate = expectedTotal > 0
                ? Math.min(100.0, (income / expectedTotal) * 100)
                : 0.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCollected",   income);
        report.put("totalExpenses",    expenses);
        report.put("balance",          balance);
        report.put("pendingDues",      pendingDues);
        report.put("profitLoss",       balance);
        report.put("collectionRate",   Math.round(collectionRate * 10.0) / 10.0);
        report.put("monthlyChart",     buildYearlyChart(year));
        report.put("pendingTrend",     buildPendingTrend(year));
        report.put("expenseBreakdown", buildExpenseBreakdownForYear(year));
        return report;
    }

    public List<Map<String, Object>> buildYearlyChart(int year) {
        List<Map<String, Object>> chart = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            double income  = safe(paymentRepo.sumPaidAmountByYearAndMonth(year, m));
            double expense = safe(expenseRepo.sumByYearAndMonth(year, m));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month",   MONTH_NAMES[m - 1]);
            point.put("income",  income);
            point.put("expense", expense);
            point.put("balance", income - expense);
            chart.add(point);
        }
        return chart;
    }

    private List<Map<String, Object>> buildPendingTrend(int year) {
        List<Map<String, Object>> trend = new ArrayList<>();
        int currentMonth = LocalDate.now().getYear() == year
                ? LocalDate.now().getMonthValue()
                : 12;

        double maintAmount = getActiveMaintAmount();
        long activeOwners = residentRepo.countAllActiveNonDeleted();

        for (int m = Math.max(1, currentMonth - 5); m <= currentMonth; m++) {
            long totalFlats = activeOwners;
            Long paidRaw = paymentRepo.countPaidByYearAndMonth(year, m);
            long paidFlats = paidRaw != null ? paidRaw : 0;
            double income = safe(paymentRepo.sumPaidAmountByYearAndMonth(year, m));
            double avgAmt = paidFlats > 0 ? income / paidFlats : maintAmount;
            double pending = (totalFlats - paidFlats) * avgAmt;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", MONTH_NAMES[m - 1]);
            point.put("pending", Math.max(0, pending));
            trend.add(point);
        }
        return trend;
    }

    private List<Map<String, Object>> buildExpenseBreakdownForYear(int year) {
        List<Expense> allExpenses = expenseRepo.findAll().stream()
                .filter(e -> e.getExpenseDate() != null
                        && e.getExpenseDate().getYear() == year)
                .collect(Collectors.toList());

        Map<String, Double> categoryTotals = allExpenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingDouble(e -> e.getAmount().doubleValue())));

        return categoryTotals.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("value"), (Double) a.get("value")))
                .collect(Collectors.toList());
    }

    private double getActiveMaintAmount() {
        return maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true)
                .map(m -> m.getAmount() != null ? m.getAmount().doubleValue() : 0.0)
                .orElse(0.0);
    }

    private double safe(Double v) {
        return v != null ? v : 0.0;
    }
}