package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.InsuranceDetailDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.InsuranceDetailService;
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
 * Personal Management (Phase 2) — Insurance. Ownership is always resolved
 * from the JWT-backed {@link Authentication} principal, never from a
 * client-supplied id, so a resident can only ever read or write their own
 * policies.
 */
@RestController
@RequestMapping("/user/insurance")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceDetailService insuranceService;
    private final ResidentService        residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InsuranceDetailDTO.Response>>> getMine(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(insuranceService.getMine(r.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceDetailDTO.Response>> getOne(
            @PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(insuranceService.getById(id, r.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InsuranceDetailDTO.Response>> add(
            @Valid @RequestBody InsuranceDetailDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        InsuranceDetailDTO.Response created = insuranceService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Insurance policy added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceDetailDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody InsuranceDetailDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Insurance policy updated",
                insuranceService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        insuranceService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Insurance policy removed", null));
    }
}
