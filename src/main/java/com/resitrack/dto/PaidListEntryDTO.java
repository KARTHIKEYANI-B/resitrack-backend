package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaidListEntryDTO {
    private Long batchPaymentId;
    private Long batchId;
    private String batchTitle;
    private String residentName;       
    private String ownerName;          
    private String familyMemberName;   
    private String flatNumber;         
    private BigDecimal amount;
    private String status;
    private LocalDate paidDate;       
    private LocalDate submittedDate;
    private String paidBy;             
    private String paidByRole;         
    private String paymentMethod;
    private String transactionId;
}