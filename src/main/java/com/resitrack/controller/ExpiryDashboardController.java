package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.ExpiryDashboardDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ExpiryDashboardService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Personal Management (Phase 3) — Expiry Management dashboard. Ownership is
 * always resolved from the JWT-backed {@link Authentication} principal.
 */
@RestController
@RequestMapping("/user/expiry-dashboard")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class ExpiryDashboardController {

    private final ExpiryDashboardService expiryDashboardService;
    private final ResidentService        residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExpiryDashboardDTO>> getDashboard(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(expiryDashboardService.getDashboard(r.getId())));
    }
}
