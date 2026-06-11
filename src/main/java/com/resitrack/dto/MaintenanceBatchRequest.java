package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MaintenanceBatchRequest {

    private String title;

    private String description;

    private String category;

    private BigDecimal amount;

    private LocalDate dueDate;

    private BigDecimal penaltyAmount;

    private boolean penaltyEnabled;

    private String assignmentType;

    private String blockPrefix;
    
    private List<String> selectedFlats;
}