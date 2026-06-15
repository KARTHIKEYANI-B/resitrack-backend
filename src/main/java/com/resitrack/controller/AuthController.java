package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.Resident;
import com.resitrack.service.AuthService;
import com.resitrack.service.ResidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService       authService;
    private final ResidentService   residentService;

    // ── Unified login (detects role automatically) ────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> unifiedLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.unifiedLogin(req)));
    }

    // ── Role-specific login endpoints (kept for backward compat) ──────────
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<JwtResponse>> adminLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.adminLogin(req)));
    }

    @PostMapping("/user/login")
    public ResponseEntity<ApiResponse<JwtResponse>> userLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.userLogin(req)));
    }

    @PostMapping("/security/login")
    public ResponseEntity<ApiResponse<JwtResponse>> securityLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.securityLogin(req)));
    }

    // ── Resident self-registration ────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationStatusDTO>> register(
            @Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        RegistrationStatusDTO status = authService.getRegistrationStatus(req.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "Registration successful! Pending admin approval.", status));
    }

    @GetMapping("/registration-status/{email}")
    public ResponseEntity<ApiResponse<RegistrationStatusDTO>> registrationStatus(
            @PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.success(authService.getRegistrationStatus(email)));
    }

    @GetMapping("/validate-register-number/{regNo}")
    public ResponseEntity<ApiResponse<Void>> validateRegNo(@PathVariable String regNo) {
        authService.validateRegisterNumber(regNo);
        return ResponseEntity.ok(ApiResponse.success("Valid register number", null));
    }

    // ── Password change endpoints ─────────────────────────────────────────
    @PutMapping("/admin/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeAdminPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        authService.changeAdminPassword(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PutMapping("/user/change-password")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> changeResidentPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        authService.changeResidentPassword(r.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PutMapping("/security/change-password")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<Void>> changeSecurityPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        authService.changeSecurityPassword(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // NOTE: Admin account listing and password reset endpoints have been moved to
    // AdminAccountController (/admin/accounts) so they fall under the existing
    // /admin/** security rule in SecurityConfig.
}