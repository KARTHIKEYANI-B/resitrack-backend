package com.resitrack.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long   maintenanceId;


    private String paymentMethod;

    private String transactionId;
    private String residentName;
    private String registerNumber;      
}