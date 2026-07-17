package com.resitrack.dto;

import com.resitrack.entity.PaymentVerificationRequestMonth;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthAllocationDTO {
    private String     month;        // "2026-05"
    private String     monthLabel;   // "May 2026"
    private BigDecimal amount;
    private Long       paymentId;    // set once verified

    public static MonthAllocationDTO from(PaymentVerificationRequestMonth m, String label) {
        return MonthAllocationDTO.builder()
                .month(m.getPaymentMonth())
                .monthLabel(label)
                .amount(m.getAmount())
                .paymentId(m.getPaymentId())
                .build();
    }
}
