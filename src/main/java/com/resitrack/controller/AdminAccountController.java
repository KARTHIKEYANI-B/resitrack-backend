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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminRepository            adminRepo;
    private final AdminAssignmentRepository  assignmentRepo;
    private final PasswordEncoder            passwordEncoder;

    // Committee-role positions a Super Admin (President) is allowed to
    // manage accounts for — deliberately excludes PRESIDENT itself (that
    // seat is the Super Admin, managed via AdminAssignmentController's
    // transfer-presidency flow instead).
    private static final Set<String> SUPER_ADMIN_MANAGEABLE_POSITIONS = Set.of(
            "VICE_PRESIDENT", "SECRETARY", "JOINT_SECRETARY", "TREASURER");

    // Create / delete / activate-deactivate / system-info stay Owner-exclusive
    // (account lifecycle for the position-based model is deliberately kept in
    // one place). List / update (name+phone) / reset-password are also open to
    // Super Admin, scoped to the four committee-role accounts above — see
    // requireOwnerOrSuperAdmin() + assertManageable().
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listAdminAccounts(
            Authentication auth) {

        Admin caller = requireOwnerOrSuperAdmin(auth);

        List<Admin> visible = adminRepo.findAll();
        if (!caller.isSystemOwner()) {
            // Super Admin: only the four committee-role accounts are visible
            visible = visible.stream()
                    .filter(a -> isSuperAdminManageable(a))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> accounts = visible.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",                  a.getId());
                    m.put("name",                a.getName());
                    m.put("email",               a.getEmail());
                    m.put("phone",               a.getPhone());
                    m.put("position",            a.getPosition() != null ? a.getPosition().name() : null);
                    m.put("superAdmin",          a.isSuperAdmin());
                    m.put("systemOwner",         a.isSystemOwner());
                    m.put("active",              a.isActive());
                    m.put("forcePasswordChange", a.isForcePasswordChange());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accounts",           accounts);
        payload.put("callerIsSuperAdmin", caller.isSuperAdmin());
        payload.put("callerIsSystemOwner", caller.isSystemOwner());

        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    // ── Owner-exclusive: create a new admin account ─────────────────────────
    // Not Super-Admin-accessible — today Admin rows are otherwise only ever
    // created implicitly via committee-position appointment
    // (AdminAssignmentService), which stays completely unchanged. This is a
    // genuinely new, independent way to create an account, for the Owner
    // tier's "manage Super Admin accounts" capability.
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAdminAccount(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        requireOwner(auth);

        String name     = body.get("name");
        String email    = body.get("email");
        String password = body.get("password");
        boolean grantSuperAdmin = "true".equalsIgnoreCase(body.get("superAdmin"));

        if (name == null || name.isBlank())
            throw new CustomException("Name is required", HttpStatus.BAD_REQUEST);
        if (email == null || email.isBlank())
            throw new CustomException("Email is required", HttpStatus.BAD_REQUEST);
        if (password == null || password.trim().length() < 6)
            throw new CustomException("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        if (adminRepo.existsByEmail(email.trim()))
            throw new CustomException("An admin with this email already exists", HttpStatus.CONFLICT);

        Admin admin = Admin.builder()
                .name(name.trim())
                .email(email.trim())
                .phone(body.getOrDefault("phone", ""))
                .password(passwordEncoder.encode(password.trim()))
                .superAdmin(grantSuperAdmin)
                .systemOwner(false) // Owner accounts are only ever seeded — never created via this generic endpoint
                .active(true)
                .forcePasswordChange(true)
                .build();
        Admin saved = adminRepo.save(admin);

        log.info("Admin account created by Owner ({}): {} (superAdmin={})",
                auth.getName(), saved.getEmail(), grantSuperAdmin);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",         saved.getId());
        result.put("name",       saved.getName());
        result.put("email",      saved.getEmail());
        result.put("superAdmin", saved.isSuperAdmin());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Admin account '" + saved.getEmail() + "' created", result));
    }

    // ── Owner: update name/phone/superAdmin. Super Admin: name/phone only,
    // and only for a committee-role target (VP/Secretary/Joint Secretary/
    // Treasurer) — see assertManageable(). ──────────────────────────────────
    @PutMapping("/{adminId}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAdminAccount(
            @PathVariable Long adminId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        Admin caller = requireOwnerOrSuperAdmin(auth);

        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException("Admin account not found", HttpStatus.NOT_FOUND));

        assertManageable(caller, target);

        // systemOwner is intentionally not settable here — Owner accounts
        // are only ever seeded (DataInitializer), never toggled at runtime,
        // so this endpoint can never create a second, differently-named
        // Owner account by accident.
        if (body.get("name") != null)       target.setName(String.valueOf(body.get("name")).trim());
        if (body.get("phone") != null)      target.setPhone(String.valueOf(body.get("phone")).trim());
        // superAdmin can only ever be granted/revoked by the Owner — a Super
        // Admin must never be able to elevate a committee account (or itself).
        if (caller.isSystemOwner() && body.get("superAdmin") != null)
            target.setSuperAdmin(Boolean.parseBoolean(String.valueOf(body.get("superAdmin"))));

        Admin saved = adminRepo.save(target);
        log.info("Admin account updated by {} ({}): {}",
                caller.isSystemOwner() ? "Owner" : "Super Admin", auth.getName(), saved.getEmail());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",         saved.getId());
        result.put("name",       saved.getName());
        result.put("email",      saved.getEmail());
        result.put("phone",      saved.getPhone());
        result.put("superAdmin", saved.isSuperAdmin());

        return ResponseEntity.ok(ApiResponse.success("Admin account updated", result));
    }

    // ── Owner-exclusive: deactivate / reactivate an admin account ──────────
    // Login-blocking (see AuthService.buildAdminResponse) without deleting
    // the row — lets an Owner suspend a Super Admin without losing their
    // history/assignments the way DELETE would require revoking first.
    @PutMapping("/{adminId}/deactivate")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivateAdminAccount(
            @PathVariable Long adminId, Authentication auth) {
        return setActiveState(adminId, auth, false);
    }

    @PutMapping("/{adminId}/activate")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> activateAdminAccount(
            @PathVariable Long adminId, Authentication auth) {
        return setActiveState(adminId, auth, true);
    }

    private ResponseEntity<ApiResponse<Void>> setActiveState(Long adminId, Authentication auth, boolean active) {
        Admin caller = requireOwner(auth);

        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException("Admin account not found", HttpStatus.NOT_FOUND));

        if (caller.getId().equals(target.getId()))
            throw new CustomException("You cannot deactivate your own account", HttpStatus.BAD_REQUEST);
        if (target.isSystemOwner())
            throw new CustomException("Cannot deactivate a System Owner account", HttpStatus.BAD_REQUEST);

        target.setActive(active);
        adminRepo.save(target);
        log.info("Admin account {} by Owner ({}): {}",
                active ? "reactivated" : "deactivated", auth.getName(), target.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "Admin account '" + target.getEmail() + "' " + (active ? "reactivated" : "deactivated"), null));
    }

    // ── Owner-exclusive: system-wide overview ───────────────────────────────
    @GetMapping("/system-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemInfo(Authentication auth) {
        requireOwner(auth);

        List<Admin> all = adminRepo.findAll();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalAdmins",      all.size());
        info.put("totalSuperAdmins", all.stream().filter(Admin::isSuperAdmin).count());
        info.put("totalOwners",      all.stream().filter(Admin::isSystemOwner).count());
        info.put("activeAdmins",     all.stream().filter(Admin::isActive).count());
        info.put("inactiveAdmins",   all.stream().filter(a -> !a.isActive()).count());
        info.put("totalCommitteeAssignments", assignmentRepo.count());

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    // Owner: any account. Super Admin: committee-role accounts only (see
    // assertManageable()).
    @PutMapping("/{adminId}/reset-password")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> resetAdminPassword(
            @PathVariable Long adminId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Admin caller = requireOwnerOrSuperAdmin(auth);

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6)
            throw new CustomException(
                    "New password must be at least 6 characters", HttpStatus.BAD_REQUEST);

        Admin target = adminRepo.findById(adminId)
                .orElseThrow(() -> new CustomException(
                        "Admin account not found", HttpStatus.NOT_FOUND));

        assertManageable(caller, target);

        String encodedPassword = passwordEncoder.encode(newPassword.trim());
        target.setPassword(encodedPassword);
        target.setForcePasswordChange(false);

        // saveAndFlush() forces immediate SQL UPDATE within the current transaction.
        // The UPDATE is committed to the DB when the transaction closes at method end.
        Admin saved = adminRepo.saveAndFlush(target);

        log.info("Password reset for admin account: {} (id={}) by {} ({})",
                saved.getEmail(), saved.getId(),
                caller.isSystemOwner() ? "Owner" : "Super Admin", auth.getName());

        // Return the email so the caller can confirm they reset the correct account.
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "Password for '" + saved.getEmail() + "' reset successfully.");
        result.put("email",   saved.getEmail());
        result.put("name",    saved.getName());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{adminId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteAdminAccount(
            @PathVariable Long adminId,
            Authentication auth) {

        Admin caller = requireOwner(auth);

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

        // Owner accounts can never be deleted via this endpoint at all
        // (same "untouchable via delete" treatment Super Admin targets
        // already get above) — matches DataInitializer, which is the only
        // place Owner accounts are ever created.
        if (target.isSystemOwner())
            throw new CustomException(
                    "Cannot delete a System Owner account.", HttpStatus.BAD_REQUEST);

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

    // Create / delete / activate-deactivate / system-info stay behind this —
    // Owner-exclusive.
    private Admin requireOwner(Authentication auth) {
        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        if (!caller.isSystemOwner())
            throw new CustomException(
                    "Only the System Owner can perform this action",
                    HttpStatus.FORBIDDEN);
        return caller;
    }

    // List / update / reset-password accept either tier; assertManageable()
    // below narrows what a Super Admin caller may actually touch.
    private Admin requireOwnerOrSuperAdmin(Authentication auth) {
        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        if (!caller.isSystemOwner() && !caller.isSuperAdmin())
            throw new CustomException(
                    "Only the System Owner or Super Admin can perform this action",
                    HttpStatus.FORBIDDEN);
        return caller;
    }

    private boolean isSuperAdminManageable(Admin a) {
        return a.getPosition() != null
                && SUPER_ADMIN_MANAGEABLE_POSITIONS.contains(a.getPosition().name())
                && !a.isSystemOwner()
                && !a.isSuperAdmin();
    }

    // Owner may act on anyone. A Super Admin caller may only act on the four
    // committee-role accounts (never on Owner or Super Admin accounts,
    // including their own) — enforced on every mutating endpoint that also
    // accepts Super Admin.
    private void assertManageable(Admin caller, Admin target) {
        if (caller.isSystemOwner()) return;
        if (!isSuperAdminManageable(target))
            throw new CustomException(
                    "Super Admin can only manage Vice President, Secretary, " +
                    "Joint Secretary, and Treasurer accounts",
                    HttpStatus.FORBIDDEN);
    }
}