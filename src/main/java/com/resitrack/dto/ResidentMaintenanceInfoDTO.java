package com.resitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Returned by GET /admin/payments/eligible-residents — powers the "Record
 * Payment" form's searchable resident dropdown (search by name or flat/villa
 * number) and, once a resident is selected, the Amount auto-calculation
 * (monthlyMaintenanceAmount × selected-months-count) — all fetched in this
 * one list, no separate per-resident lookup call needed.
 */
@Data
@Builder
public class ResidentMaintenanceInfoDTO {
    private Long residentId;
    private String ownerName;
    private String flatNumber;
    private String phone;
    private BigDecimal monthlyMaintenanceAmount;
}
