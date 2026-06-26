package com.resitrack.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceListDTO {

    private String     paymentMonth;            
    private String     monthLabel;              
    private BigDecimal ratePerSqFt;             
    private BigDecimal flatRatePerSqFt;
    private BigDecimal villaRatePerSqFt;
    private BigDecimal flatAmount;            

    private List<MaintenanceOwnerDTO> flatOwners;
    private List<MaintenanceOwnerDTO> villaOwners;

    private BigDecimal totalFlatMaintenance;
    private BigDecimal totalVillaMaintenance;
    private BigDecimal grandTotal;

    // ── Total PAID amount (collected), as opposed to the billed/due totals
    //    above. Summed from each owner's paidAmount — which is itself
    //    sumPaidAmountByPropertyAndPaymentMonth(owner, paymentMonth), the
    //    same query already used per-row in this DTO's owner lists. This is
    //    the single authoritative "amount actually collected this month"
    //    figure: Admin Dashboard's Collected Amount and Admin → Paid/Unpaid
    //    Details' monthly total must both equal grandTotalPaid for the same
    //    paymentMonth.
    private BigDecimal totalFlatPaid;
    private BigDecimal totalVillaPaid;
    private BigDecimal grandTotalPaid;

    private int totalFlatOwners;
    private int totalVillaOwners;
    private int paidFlatOwners;
    private int paidVillaOwners;
}