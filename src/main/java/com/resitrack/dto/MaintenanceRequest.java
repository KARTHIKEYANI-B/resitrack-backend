package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MaintenanceRequest {
    private String     maintenanceType;
   
    private BigDecimal ratePerSqFt;

    private Double     amount;
    private LocalDate  dueDate;
    private Double     lateFee;
    private boolean    lateFeeEnabled;
}