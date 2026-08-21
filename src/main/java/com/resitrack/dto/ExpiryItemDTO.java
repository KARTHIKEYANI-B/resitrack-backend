package com.resitrack.dto;

import lombok.*;

import java.time.LocalDate;

/** One row in the Personal Management Expiry Dashboard (Phase 3). */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpiryItemDTO {
    private String    recordType;   // INSURANCE | LICENSE | DOCUMENT
    private Long       recordId;
    private String    name;         // display label, e.g. "Health Insurance — HLT-001"
    private LocalDate expiryDate;
    private String    status;           // raw manual status
    private String    effectiveStatus;  // Active | Expiring Soon | Expired | Cancelled | Suspended
}
