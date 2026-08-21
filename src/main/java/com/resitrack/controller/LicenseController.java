package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.LicenseDetailDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.LicenseDetailService;
import com.resitrack.service.ResidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Personal Management (Phase 2) — Licenses. Ownership is always resolved
 * from the JWT-backed {@link Authentication} principal, never from a
 * client-supplied id, so a resident can only ever read or write their own
 * licenses.
 */
@RestController
@RequestMapping("/user/licenses")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseDetailService licenseService;
    private final ResidentService      residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LicenseDetailDTO.Response>>> getMine(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(licenseService.getMine(r.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LicenseDetailDTO.Response>> getOne(
            @PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(licenseService.getById(id, r.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseDetailDTO.Response>> add(
            @Valid @RequestBody LicenseDetailDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        LicenseDetailDTO.Response created = licenseService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("License added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LicenseDetailDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody LicenseDetailDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("License updated",
                licenseService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        licenseService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("License removed", null));
    }
}
