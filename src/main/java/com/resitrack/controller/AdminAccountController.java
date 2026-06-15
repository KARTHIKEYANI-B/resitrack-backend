package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.entity.AdminAssignment;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminAssignmentRepository;
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
 * Mapped to /admin/accounts, covered by SecurityConfig:
 *   .requestMatchers("/admin/**").hasRole("ADMIN")
 *
 * All write operations additionally enforce isSuperAdmin() at method level.
 */
@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminRepository            adminRepo;
    private final AdminAssignmentRepository  assignmentRepo;
    private final PasswordEncoder            passwordEncoder;

    /**
     * GET /api/admin/accounts
     * Returns all admin accounts. Super Admin / President only.
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
     * Super Admin / President only. Sets a new password without requiring the current one.
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

    /**
     * DELETE /api/admin/accounts/{adminId}
     *
     * Super Admin / President only.
     * Removes a stale or duplicate admin account from the database.
     *
     * Safety rules enforced server-side:
     *  - Cannot delete your own account.
     *  - Cannot delete any account where superAdmin=true
     *    (protects canonical Super Admin and active President).
     *  - Cannot delete an account with an active committee assignment.
     *  - Deletes historical AdminAssignment rows first (FK constraint).
     */
    @DeleteMapping("/{adminId}")
    public ResponseEntity<ApiResponse<Void>> deleteAdminAccount(
            @PathVariable Long adminId,
            Authentication auth) {

        requireSuperAdmin(auth);

        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));

        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException(
                        "Admin account not found", HttpStatus.NOT_FOUND));

        if (caller.getId().equals(target.getId()))
            throw new CustomException(
                    "You cannot delete your own account", HttpStatus.BAD_REQUEST);

        if (target.isSuperAdmin())
            throw new CustomException(
                    "Cannot delete a Super Admin / President account. " +
                    "Transfer presidency first if needed.", HttpStatus.BAD_REQUEST);

        boolean hasActiveAssignment = assignmentRepo
                .findByAdminIdAndActiveTrue(target.getId())
                .isPresent();
        if (hasActiveAssignment)
            throw new CustomException(
                    "Cannot delete '" + target.getEmail() +
                    "' — it has an active committee assignment. Revoke it first.",
                    HttpStatus.CONFLICT);

        List<AdminAssignment> history = assignmentRepo.findByAdmin(target);
        if (!history.isEmpty()) assignmentRepo.deleteAll(history);

        adminRepo.delete(target);

        return ResponseEntity.ok(ApiResponse.success(
                "Admin account '" + target.getEmail() + "' deleted", null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireSuperAdmin(Authentication auth) {
        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        if (!caller.isSuperAdmin())
            throw new CustomException(
                    "Only Super Admin / President can manage admin accounts",
                    HttpStatus.FORBIDDEN);
    }
}