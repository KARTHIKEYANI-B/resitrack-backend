package com.resitrack.dto;

import lombok.*;

import java.util.List;

/** Personal Management → Expiry Management dashboard (Phase 3). */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpiryDashboardDTO {
    private List<ExpiryItemDTO> active;
    private List<ExpiryItemDTO> expiringSoon;
    private List<ExpiryItemDTO> expired;
}
