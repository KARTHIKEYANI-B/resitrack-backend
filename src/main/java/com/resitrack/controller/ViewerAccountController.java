package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
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
import java.util.Map;

/**
 * Dedicated endpoint for creating Viewer accounts.
 *
 * Kept as a separate controller (rather than a sub-path on
 * AdminAccountController) to avoid Spring's path-variable ambiguity:
 * AdminAccountController already maps /{adminId} for PUT/DELETE, so
 * adding @PostMapping("/viewer") under the same base path caused Spring
 * to match "viewer" as an adminId on the existing PUT/DELETE handlers
 * when the app had not yet been rebuilt with the new mapping, resulting
 * in "Supported methods: DELETE, PUT".
 *
 * This controller lives at /admin/viewer-accounts and has no /{id}
 * sub-paths, so there is no ambiguity.
 */
@Slf4j
@RestController
@RequestMapping("/admin/viewer-accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ViewerAccountController {

    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;

    /**
     * POST /admin/viewer-accounts
     *
     * Creates a Viewer account. Accessible by System Owner and Super Admin.
     * A Viewer can log in and view all SuperAdmin-visible pages but cannot
     * perform any write operation — enforced at every write endpoint via
     * ViewerGuard.rejectViewer().
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createViewerAccount(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Admin caller = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));

        // Only System Owner or Super Admin can create Viewer accounts
        if (!caller.isSystemOwner() && !caller.isSuperAdmin()) {
            throw new CustomException(
                    "Only the System Owner or Super Admin can create Viewer accounts",
                    HttpStatus.FORBIDDEN);
        }

        // A Viewer cannot create other accounts
        if (caller.isViewer()) {
            throw new CustomException(
                    "Viewer accounts are read-only and cannot perform write operations.",
                    HttpStatus.FORBIDDEN);
        }

        String name     = body.get("name");
        String email    = body.get("email");
        String password = body.get("password");

        if (name == null || name.isBlank())
            throw new CustomException("Name is required", HttpStatus.BAD_REQUEST);
        if (email == null || email.isBlank())
            throw new CustomException("Email is required", HttpStatus.BAD_REQUEST);
        if (password == null || password.trim().length() < 6)
            throw new CustomException("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        if (adminRepo.existsByEmail(email.trim()))
            throw new CustomException(
                    "An account with this email already exists", HttpStatus.CONFLICT);

        Admin admin = Admin.builder()
                .name(name.trim())
                .email(email.trim())
                .phone(body.getOrDefault("phone", ""))
                .password(passwordEncoder.encode(password.trim()))
                .superAdmin(false)
                .systemOwner(false)
                .viewer(true)
                .active(true)
                .forcePasswordChange(false)
                .build();

        Admin saved = adminRepo.save(admin);

        log.info("Viewer account created by {} ({}): {}",
                caller.isSystemOwner() ? "Owner" : "Super Admin",
                auth.getName(), saved.getEmail());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",     saved.getId());
        result.put("name",   saved.getName());
        result.put("email",  saved.getEmail());
        result.put("viewer", true);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Viewer account '" + saved.getEmail() + "' created", result));
    }
}