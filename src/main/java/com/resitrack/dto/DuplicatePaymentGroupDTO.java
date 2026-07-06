package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DuplicatePaymentGroupDTO {
    private Long residentId;
    private String residentName;
    private String flatNumber;
    private String paymentMonth;
    private long duplicateCount;
    private BigDecimal totalAmount;
    private List<PaymentResponseDTO> payments;
}