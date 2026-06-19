package com.resitrack.service;

import com.resitrack.dto.DashboardStatsDTO;
import com.resitrack.dto.MaintenanceListDTO;
import com.resitrack.dto.MaintenanceOwnerDTO;
import com.resitrack.dto.MonthlyChartDTO;
import com.resitrack.entity.Payment;
import com.resitrack.entity.PropertyType;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PaymentRepository                    paymentRepo;
    private final ExpenseRepository                    expenseRepo;
    private final ResidentRepository                   residentRepo;
    private final MaintenanceRepository                maintenanceRepo;
    private final MaintenanceService                   maintenanceService;
    private final PaymentVerificationRequestRepository verificationRepo;

    public DashboardStatsDTO getAdminStats() {
        return getAdminStats(LocalDate.now().getYear(), LocalDate.now().getMonthValue());
    }

    /**
     * Computes all admin dashboard statistics for the given month/year.
     *
     * ═══════════════════════════════════════════════════════════════════════
     * SINGLE SOURCE OF TRUTH: Admin → Maintenance List
     * ═══════════════════════════════════════════════════════════════════════
     *
     * All payment-status counts are derived from the Maintenance List DTOs.
     * This ensures the Dashboard always agrees exactly with what the admin sees
     * on the Maintenance List page — no separate calculation, no divergence.
     *
     * Status derivation (mirrors MaintenanceService.buildOwnerDTOs):
     *
     *   "PAID"    → pendingAmount == 0  → fullPaymentCount++
     *               (includes properties where a Family Member made the final payment)
     *
     *   "PARTIAL" → paidAmount > 0 AND pendingAmount > 0  → partialPaymentCount++
     *
     *   "UNPAID"  → paidAmount == 0                       → fullUnpaidCount++
     *
     * Family Member payments are fully included because the Maintenance List
     * uses sumPaidAmountByPropertyAndPaymentMonth which aggregates owner + FM payments.
     */
    public DashboardStatsDTO getAdminStats(int year, int month) {

        // ── Financial figures ─────────────────────────────────────────────
        Double income = paymentRepo.sumPaidAmountByYearAndMonth(year, month);
        income = income == null ? 0.0 : income;

        Double bankExpense = expenseRepo.sumBankExpenseByYearAndMonth(year, month);
        Double cashExpense = expenseRepo.sumCashExpenseByYearAndMonth(year, month);
        bankExpense = bankExpense == null ? 0.0 : bankExpense;
        cashExpense = cashExpense == null ? 0.0 : cashExpense;
        double totalExpense = bankExpense + cashExpense;

        Double bankPaid = paymentRepo.sumBankCollectedByYearAndMonth(year, month);
        Double cashPaid = paymentRepo.sumCashCollectedByYearAndMonth(year, month);
        bankPaid = bankPaid == null ? 0.0 : bankPaid;
        cashPaid = cashPaid == null ? 0.0 : cashPaid;

        double bankBalance  = bankPaid  - bankExpense;
        double cashBalance  = cashPaid  - cashExpense;
        double totalBalance = income    - totalExpense;

        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear  = month == 1 ? year - 1 : year;
        Double prevIncome  = paymentRepo.sumPaidAmountByYearAndMonth(prevYear, prevMonth);
        Double prevBankExp = expenseRepo.sumBankExpenseByYearAndMonth(prevYear, prevMonth);
        Double prevCashExp = expenseRepo.sumCashExpenseByYearAndMonth(prevYear, prevMonth);
        prevIncome  = prevIncome  == null ? 0.0 : prevIncome;
        double prevExpense = (prevBankExp == null ? 0.0 : prevBankExp)
                           + (prevCashExp == null ? 0.0 : prevCashExp);

        double revenueGrowth = prevIncome  > 0 ? ((income      - prevIncome)  / prevIncome  * 100) : 0.0;
        double expenseGrowth = prevExpense > 0 ? ((totalExpense - prevExpense) / prevExpense * 100) : 0.0;

        // ── Active owner counts ───────────────────────────────────────────
        long activeFlatOwners  = residentRepo.countActiveOwnersByPropertyType(PropertyType.FLAT);
        long activeVillaOwners = residentRepo.countActiveOwnersByPropertyType(PropertyType.VILLA);
        long occupiedFlats     = activeFlatOwners + activeVillaOwners;

        Double totalCollections = paymentRepo.sumAllPaidAmount();
        totalCollections = totalCollections == null ? 0.0 : totalCollections;

        // ── Payment status counts from Maintenance List ───────────────────
        //
        // Derive counts from the same DTOs the Maintenance List renders.
        // Each DTO has paymentStatus ("PAID" / "PARTIAL" / "UNPAID") and
        // pendingAmount already computed with property-level FM aggregation.
        //
        // This guarantees zero divergence between Dashboard and Maintenance List.
        // Wrapped in try-catch so a calculation edge case never blocks the dashboard.
        List<MaintenanceOwnerDTO> allOwnerDTOs = new ArrayList<>();
        try {
            MaintenanceListDTO maintList = maintenanceService.getOwnerMaintenanceList(year, month);
            if (maintList.getFlatOwners()  != null) allOwnerDTOs.addAll(maintList.getFlatOwners());
            if (maintList.getVillaOwners() != null) allOwnerDTOs.addAll(maintList.getVillaOwners());
        } catch (Exception ignored) {
            // If maintenance list calculation fails, counts default to 0 — dashboard still loads
        }

        int    fullPaymentCount    = 0;   // PAID (pendingAmount == 0)
        int    partialPaymentCount = 0;   // PARTIAL
        int    fullUnpaidCount     = 0;   // UNPAID (no payment at all)
        int    paidCount           = 0;   // same as fullPaymentCount (alias)
        int    unpaidOwnerCount    = 0;   // PARTIAL + UNPAID
        double totalPendingAmount  = 0.0;

        for (MaintenanceOwnerDTO dto : allOwnerDTOs) {
            String     status  = dto.getPaymentStatus();
            BigDecimal pending = dto.getPendingAmount() != null
                    ? dto.getPendingAmount() : BigDecimal.ZERO;

            if ("PAID".equals(status)) {
                // pendingAmount == 0 → fully paid (owner or FM or combination)
                fullPaymentCount++;
                paidCount++;
            } else if ("PARTIAL".equals(status)) {
                partialPaymentCount++;
                unpaidOwnerCount++;
                totalPendingAmount += pending.doubleValue();
            } else {
                // "UNPAID" — no payment at all
                fullUnpaidCount++;
                unpaidOwnerCount++;
                totalPendingAmount += pending.doubleValue();
            }
        }

        double rate = occupiedFlats > 0 ? (paidCount * 100.0 / occupiedFlats) : 0.0;

        return DashboardStatsDTO.builder()
                .totalMonthlyIncome(income)
                .totalMonthlyExpense(totalExpense)
                .balance(totalBalance)
                .bankBalance(bankBalance)
                .cashBalance(cashBalance)
                .bankExpense(bankExpense)
                .cashExpense(cashExpense)
                .flatsPaid(paidCount)
                .paidMaintenanceCount(paidCount)
                .unpaidMaintenanceCount(unpaidOwnerCount)
                .totalFlats((int) occupiedFlats)
                .occupiedFlats((int) occupiedFlats)
                .vacantFlats(0)
                .pendingAmount(Math.round(totalPendingAmount * 100.0) / 100.0)
                .totalCollections(totalCollections)
                .collectionRate(Math.min(100.0, Math.round(rate * 10.0) / 10.0))
                .revenueGrowth(Math.round(revenueGrowth * 10.0) / 10.0)
                .expenseGrowth(Math.round(expenseGrowth * 10.0) / 10.0)
                .flatOwners((int) activeFlatOwners)
                .villaOwners((int) activeVillaOwners)
                .activeFlatOwners((int) activeFlatOwners)
                .activeVillaOwners((int) activeVillaOwners)
                .fullPaymentCount(fullPaymentCount)
                //.partialPaymentCount(partialPaymentCount)
                .fullUnpaidCount(fullUnpaidCount)
                .build();
    }

    // ─── User dashboard stats ─────────────────────────────────────────────
    public Map<String, Object> getUserStats(Long residentId) {
        List<Payment> payments = paymentRepo.findByResidentId(residentId);
        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        String currentMonth = year + "-" + String.format("%02d", month);

        // Guard against any payment with a null amount field (defensive — DB can have legacy rows)
        double totalPaid = payments.stream()
                .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.PAID
                          && p.getAmount() != null)
                .mapToDouble(p -> p.getAmount().doubleValue()
                        + (p.getLateFeeAmount() != null ? p.getLateFeeAmount().doubleValue() : 0.0))
                .sum();

        Optional<Payment> lastPayment = payments.stream()
                .filter(p -> p.getPaymentStatus() == Payment.PaymentStatus.PAID
                          && p.getPaymentDate() != null
                          && p.getAmount() != null)
                .max(Comparator.comparing(Payment::getPaymentDate));

        double lastAmount = lastPayment
                .map(p -> p.getAmount().doubleValue()
                        + (p.getLateFeeAmount() != null ? p.getLateFeeAmount().doubleValue() : 0.0))
                .orElse(0.0);

        Double totalPaidThisMonth = paymentRepo.sumPaidAmountByPropertyAndPaymentMonth(
                residentId, currentMonth);
        totalPaidThisMonth = totalPaidThisMonth == null ? 0.0 : totalPaidThisMonth;

        boolean hasAnyPaid = totalPaidThisMonth > 0;

        boolean hasPendingVerificationInPayments = payments.stream()
                .anyMatch(p -> currentMonth.equals(p.getPaymentMonth())
                        && p.getPaymentStatus() == Payment.PaymentStatus.PENDING_VERIFICATION);

        boolean hasPendingVerificationRequest = verificationRepo
                .existsPendingByResidentIdAndPaymentMonth(residentId, currentMonth);

        boolean hasPendingVerification = hasPendingVerificationInPayments || hasPendingVerificationRequest;

        String paymentStatus = hasAnyPaid ? "PAID"
                : hasPendingVerification  ? "PENDING_VERIFICATION"
                : "UNPAID";

        com.resitrack.entity.Maintenance activeMaintEntity =
                maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true).orElse(null);

        double currentDueAmount = 0.0;
        double lateFee          = 0.0;
        String dueDate          = null;
        boolean lateFeeApplied  = false;

        if (activeMaintEntity != null) {
            try {
                com.resitrack.entity.Resident owner = residentRepo.findById(residentId).orElse(null);
                Double ownerSqFt = owner != null ? owner.getSqFt() : null;
                java.math.BigDecimal calcAmount =
                        maintenanceService.calculateAmountForResident(activeMaintEntity, ownerSqFt);
                currentDueAmount = calcAmount != null ? calcAmount.doubleValue() : 0.0;
            } catch (Exception e) {
                currentDueAmount = activeMaintEntity.getAmount() != null
                        ? activeMaintEntity.getAmount().doubleValue() : 0.0;
            }

            dueDate = activeMaintEntity.getDueDate() != null
                    ? activeMaintEntity.getDueDate().toString() : null;

            if (Boolean.TRUE.equals(activeMaintEntity.getLateFeeEnabled())
                    && activeMaintEntity.getDueDate() != null
                    && LocalDate.now().isAfter(activeMaintEntity.getDueDate())
                    && !hasAnyPaid) {
                lateFee = activeMaintEntity.getLateFee() != null
                        ? activeMaintEntity.getLateFee().doubleValue() : 0.0;
                lateFeeApplied = true;
            }
        }

        double totalAssigned = currentDueAmount + lateFee;
        double pendingDue    = Math.max(0.0, totalAssigned - totalPaidThisMonth);

        String currentTxnId = payments.stream()
                .filter(p -> currentMonth.equals(p.getPaymentMonth())
                        && p.getPaymentStatus() == Payment.PaymentStatus.PAID)
                .max(Comparator.comparing(p -> p.getPaymentDate() != null
                        ? p.getPaymentDate() : LocalDate.MIN))
                .map(Payment::getTransactionId)
                .orElse(null);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPaidAmount",   totalPaid);
        stats.put("lastPaymentAmount", lastAmount);
        stats.put("lastPaymentDate",   lastPayment.map(p -> p.getPaymentDate().toString()).orElse(null));
        stats.put("lastPaymentMethod", lastPayment.map(Payment::getPaymentMethod).orElse(null));
        stats.put("paymentStatus",     paymentStatus);
        stats.put("currentDue",        pendingDue);
        stats.put("currentMonthDue",   currentDueAmount);
        stats.put("paidAmount",        totalPaidThisMonth);
        stats.put("lateFee",           lateFee);
        stats.put("lateFeeApplied",    lateFeeApplied);
        stats.put("dueDate",           dueDate);
        stats.put("currentMonth",      currentMonth);
        stats.put("transactionId",     currentTxnId);
        stats.put("totalAssigned",     totalAssigned);
        return stats;
    }

    // ─── Yearly chart ─────────────────────────────────────────────────────
    public List<MonthlyChartDTO> getYearlyChart(int year) {
        String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        List<MonthlyChartDTO> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Double inc = paymentRepo.sumPaidAmountByYearAndMonth(year, m);
            Double exp = expenseRepo.sumByYearAndMonth(year, m);
            inc = inc == null ? 0.0 : inc;
            exp = exp == null ? 0.0 : exp;
            result.add(new MonthlyChartDTO(months[m - 1], inc, exp, inc - exp));
        }
        return result;
    }
}