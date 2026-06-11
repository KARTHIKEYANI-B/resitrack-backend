package com.resitrack.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceListDTO {

    private String     paymentMonth;            
    private String     monthLabel;              
    private BigDecimal ratePerSqFt;             
    private BigDecimal flatAmount;            

    private List<MaintenanceOwnerDTO> flatOwners;
    private List<MaintenanceOwnerDTO> villaOwners;

    private BigDecimal totalFlatMaintenance;
    private BigDecimal totalVillaMaintenance;
    private BigDecimal grandTotal;

    private int totalFlatOwners;
    private int totalVillaOwners;
    private int paidFlatOwners;
    private int paidVillaOwners;
}