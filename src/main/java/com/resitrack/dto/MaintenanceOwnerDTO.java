package com.resitrack.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceOwnerDTO {

    private Long       residentId;
    private String     fullName;
    private String     flatNumber;
    private String     flatType;
    private String     propertyType;       

    private Double     sqFt;              
    private BigDecimal ratePerSqFt;      
    private BigDecimal maintenanceAmount;  

    private String     paymentStatus;     
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private String     paymentMonth;     
}