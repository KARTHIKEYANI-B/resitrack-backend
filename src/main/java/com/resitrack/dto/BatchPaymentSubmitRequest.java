package com.resitrack.dto;

import lombok.Data;

@Data
public class BatchPaymentSubmitRequest {
    private String paymentMethod;   
    private String transactionId;
}
