package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentTrackingStatsDTO {
    private long   totalRegisteredOwners;
    private long   totalActiveOwners;
    private long   paidOwners;
    private long   unpaidOwners;
    private long   overdueOwners;
    private long   pendingVerification;
    private double collectionPercentage;
    private double totalCollectedThisMonth;
    private double bankCollectedThisMonth;
    private double cashCollectedThisMonth;
    private double totalPendingAmount;      

    private double annualPendingDues;
    private double totalExpectedYTD;
    private double totalCollectedYTD;

    private String currentMonth;
}