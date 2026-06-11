package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminPaymentRequest {

    private String ownerPhone;

    private String ownerName;

    private String paymentMode;

    private BigDecimal paidAmount;

    private LocalDate paymentDate;

    private String paymentMonth;

    private Boolean verifiedByAdmin;

    private String transactionId;

    private String description;
}
