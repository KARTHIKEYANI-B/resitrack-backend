package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AdminAccountController — Super Admin account management.
 *
 * Mapped to /admin/accounts so it falls under the existing SecurityConfig rule:
 *   .requestMatchers("/admin/**").hasRole("ADMIN")
 *
 * Previously these endpoints were placed inside AuthController under /auth/admin/accounts,
 * which was outside the /admin/** security rule and caused Spring to fail to locate
 * the handler, producing:
 *   "No static resource auth/admin/accounts"
 *
 * Moving them here fixes that without touching SecurityConfig, AuthController,
 * or any other existing feature.
 */
@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;

    /**
     * GET /api/admin/accounts
     *
     * Returns all admin accounts (id, name, email, phone, position, superAdmin, forcePasswordChange).
     * Super Admin only. Used by the Admin Accounts tab in MembersList → AdminAccountsPanel.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAdminAccounts(
            Authentication auth) {

        requireSuperAdmin(auth);

        List<Map<String, Object>> accounts = adminRepo.findAll().stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",                  a.getId());
                    m.put("name",                a.getName());
                    m.put("email",               a.getEmail());
                    m.put("phone",               a.getPhone());
                    m.put("position",            a.getPosition() != null ? a.getPosition().name() : null);
                    m.put("superAdmin",          a.isSuperAdmin());
                    m.put("forcePasswordChange", a.isForcePasswordChange());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    /**
     * PUT /api/admin/accounts/{adminId}/reset-password
     *
     * Super Admin only. Sets a new password for any admin account without requiring
     * the current password. Used when position account credentials are unknown.
     *
     * Body: { "newPassword": "NewSecure@123" }
     */
    @PutMapping("/{adminId}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetAdminPassword(
            @PathVariable Long adminId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        requireSuperAdmin(auth);

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6)
            throw new CustomException(
                    "New password must be at least 6 characters", HttpStatus.BAD_REQUEST);

        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException(
                        "Admin account not found", HttpStatus.NOT_FOUND));

        target.setPassword(passwordEncoder.encode(newPassword.trim()));
        target.setForcePasswordChange(false);
        adminRepo.save(target);

        return ResponseEntity.ok(ApiResponse.success(
                "Password for " + target.getName() + " reset successfully", null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireSuperAdmin(Authentication auth) {
        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        if (!caller.isSuperAdmin())
            throw new CustomException(
                    "Only Super Admin can manage admin accounts", HttpStatus.FORBIDDEN);
    }
}