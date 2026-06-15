package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.entity.AdminAssignment;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminAssignmentRepository;
import com.resitrack.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AdminAccountController — Admin account management.
 *
 * Mapped to /admin/accounts, covered by SecurityConfig:
 *   .requestMatchers("/admin/**").hasRole("ADMIN")
 *
 * Permission model:
 *   GET  (list)           — any authenticated admin can view
 *   PUT  (reset-password) — Super Admin only
 *   DELETE                — Super Admin only
 */
@Slf4j
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
     * Returns all admin accounts visible to any authenticated admin.
     * The response includes a "callerIsSuperAdmin" flag so the frontend
     * can show or hide edit / reset-password controls accordingly.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listAdminAccounts(
            Authentication auth) {

        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));

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

        // Return accounts + caller permission flag so the frontend can
        // conditionally show reset-password / delete controls.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accounts",          accounts);
        payload.put("callerIsSuperAdmin", caller.isSuperAdmin());

        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    /**
     * PUT /api/admin/accounts/{adminId}/reset-password
     *
     * Super Admin / President only. Sets a new password without requiring the current one.
     * Body: { "newPassword": "NewSecure@123" }
     *
     * FIX — password reset not persisting to DB:
     *   @Transactional  → wraps the entire method in one transaction so the UPDATE
     *                     is committed atomically before the response is returned.
     *   saveAndFlush()  → forces an immediate SQL UPDATE within the transaction
     *                     instead of waiting for the session to flush at commit time.
     *                     Eliminates any possibility of a stale first-level cache
     *                     preventing the write from reaching the database.
     *
     * The response body now echoes the email of the account that was actually reset
     * so the caller can verify they reset the correct account.
     */
    @PutMapping("/{adminId}/reset-password")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> resetAdminPassword(
            @PathVariable Long adminId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        requireSuperAdmin(auth);

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6)
            throw new CustomException(
                    "New password must be at least 6 characters", HttpStatus.BAD_REQUEST);

        // Re-fetch within this transaction to get the latest state from the DB.
        // This prevents any stale entity state from a previous read in the same session.
        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException(
                        "Admin account not found", HttpStatus.NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(newPassword.trim());
        target.setPassword(encodedPassword);
        target.setForcePasswordChange(false);

        // saveAndFlush() forces immediate SQL UPDATE within the current transaction.
        // The UPDATE is committed to the DB when the transaction closes at method end.
        Admin saved = adminRepo.saveAndFlush(target);

        log.info("Password reset for admin account: {} (id={})", saved.getEmail(), saved.getId());

        // Return the email so the caller can confirm they reset the correct account.
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "Password for '" + saved.getEmail() + "' reset successfully.");
        result.put("email",   saved.getEmail());
        result.put("name",    saved.getName());

        return ResponseEntity.ok(ApiResponse.success(result));
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
    @Transactional
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
        log.info("Admin account deleted: {} (id={})", target.getEmail(), target.getId());

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