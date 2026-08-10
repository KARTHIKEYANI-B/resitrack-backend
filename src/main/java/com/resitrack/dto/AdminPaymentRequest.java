package com.resitrack.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminPaymentRequest {

    /** Resident (owner) selected from the searchable Resident dropdown. */
    private Long residentId;

    private String paymentMode;

    private BigDecimal paidAmount;

    private LocalDate paymentDate;

    private String paymentMonth;

    private Boolean verifiedByAdmin;

    private String transactionId;

    private String description;
}
