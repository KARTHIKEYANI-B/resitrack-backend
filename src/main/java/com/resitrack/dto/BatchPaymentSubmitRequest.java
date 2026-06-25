package com.resitrack.dto;

import lombok.Data;

@Data
public class BatchPaymentSubmitRequest {
    private String paymentMethod;   // UPI, BANK_TRANSFER, CASH
    private String transactionId;
}
