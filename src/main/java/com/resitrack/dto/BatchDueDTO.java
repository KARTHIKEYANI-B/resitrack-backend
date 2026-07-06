package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BatchDueDTO {
    private Long batchPaymentId;
    private Long batchId;
    private String batchTitle;     
    private String category;
    private String description;
    private BigDecimal amount;    
    private LocalDate dueDate;
    private String status;         
    private String paymentMethod;
    private String transactionId;
    private LocalDate submittedDate;
    private LocalDate verifiedDate;
    private String rejectionReason;
}
