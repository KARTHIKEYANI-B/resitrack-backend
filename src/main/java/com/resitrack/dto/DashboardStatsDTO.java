package com.resitrack.dto;

import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class DashboardStatsDTO {

    private Double  totalMonthlyIncome;
    private Double  totalMonthlyExpense; 
    private Double  balance;       
    private Double  bankBalance;   
    private Double  cashBalance;   
    private Double  bankExpense;   
    private Double  cashExpense;   
    private Integer flatsPaid;
    private Integer totalFlats;
    private Integer paidMaintenanceCount;
    private Integer unpaidMaintenanceCount;
    private Integer occupiedFlats;
    private Integer vacantFlats;
    private Double  pendingAmount;
    private Double  totalCollections;
    private Double  collectionRate;
    private Double  revenueGrowth;
    private Double  expenseGrowth;
    private Integer flatOwners;
    private Integer villaOwners;
    private Integer activeFlatOwners;
    private Integer activeVillaOwners;
    private Integer fullPaymentCount;
    private Integer fullUnpaidCount;  
}