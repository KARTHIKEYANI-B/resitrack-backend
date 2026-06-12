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

    private final AuthService     authService;
    private final ResidentService residentService;

    // existing — unchanged
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<JwtResponse>> adminLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.adminLogin(req)));
    }

    // existing — unchanged
    @PostMapping("/user/login")
    public ResponseEntity<ApiResponse<JwtResponse>> userLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.userLogin(req)));
    }

    // NEW — security guard login
    @PostMapping("/security/login")
    public ResponseEntity<ApiResponse<JwtResponse>> securityLogin(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.securityLogin(req)));
    }

    // existing — unchanged
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationStatusDTO>> register(
            @Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        RegistrationStatusDTO status = authService.getRegistrationStatus(req.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "Registration successful! Pending admin approval.", status));
    }

    // existing — unchanged
    @GetMapping("/registration-status/{email}")
    public ResponseEntity<ApiResponse<RegistrationStatusDTO>> registrationStatus(
            @PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.success(authService.getRegistrationStatus(email)));
    }

    // existing — unchanged
    @GetMapping("/validate-register-number/{regNo}")
    public ResponseEntity<ApiResponse<Void>> validateRegNo(@PathVariable String regNo) {
        authService.validateRegisterNumber(regNo);
        return ResponseEntity.ok(ApiResponse.success("Valid register number", null));
    }

    // existing — unchanged
    @PutMapping("/admin/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeAdminPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        authService.changeAdminPassword(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // existing — unchanged
    @PutMapping("/user/change-password")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> changeResidentPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        authService.changeResidentPassword(r.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // NEW — security guard change password
    @PutMapping("/security/change-password")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<Void>> changeSecurityPassword(
            @RequestBody ChangePasswordRequest req, Authentication auth) {
        authService.changeSecurityPassword(auth.getName(), req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}