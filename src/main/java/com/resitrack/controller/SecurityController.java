package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.SecurityGuardDTO;
import com.resitrack.dto.SecurityResidentDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Notification;
import com.resitrack.entity.SecurityGuard;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.SecurityGuardRepository;
import com.resitrack.service.SecurityGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityGuardService    securityService;
    private final SecurityGuardRepository guardRepo;
    private final AdminRepository         adminRepo;
    private final NotificationRepository  notifRepo;

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN → VIEW endpoints  (all Admin roles can read)
    // ══════════════════════════════════════════════════════════════════════

    /** All admins can list security accounts (read-only view). */
    @GetMapping("/admin/security")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SecurityGuardDTO.Response>>> listGuards() {
        return ResponseEntity.ok(ApiResponse.success(securityService.getAll()));
    }

    /** All admins can fetch a single security account (read-only view). */
    @GetMapping("/admin/security/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SecurityGuardDTO.Response>> getGuard(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(securityService.getById(id)));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN → MANAGE endpoints  (Super Admin / President only)
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/security")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SecurityGuardDTO.Response>> createGuard(
            @RequestBody SecurityGuardDTO.Request req, Authentication auth) {
        requireSuperAdmin(auth);
        Admin admin = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.UNAUTHORIZED));
        SecurityGuardDTO.Response created = securityService.create(req, admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Security account created successfully", created));
    }

    @PutMapping("/admin/security/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SecurityGuardDTO.Response>> updateGuard(
            @PathVariable Long id, @RequestBody SecurityGuardDTO.UpdateRequest req,
            Authentication auth) {
        requireSuperAdmin(auth);
        Admin admin = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(
                "Security account updated", securityService.update(id, req, admin.getId())));
    }

    @DeleteMapping("/admin/security/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGuard(
            @PathVariable Long id, Authentication auth) {
        requireSuperAdmin(auth);
        securityService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Security account deleted", null));
    }

    /** Send a message to a security guard — any authenticated Admin role. */
    @PostMapping("/admin/security/{guardId}/message")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> sendMessageToGuard(
            @PathVariable Long guardId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        SecurityGuard guard = guardRepo.findById(guardId)
                .orElseThrow(() -> new CustomException(
                        "Security account not found", HttpStatus.NOT_FOUND));

        String title   = body.getOrDefault("title", "Message from Admin");
        String message = body.getOrDefault("message", "");
        if (message.isBlank())
            throw new CustomException("Message cannot be empty", HttpStatus.BAD_REQUEST);

        Notification notif = Notification.builder()
                .title(title)
                .message(message)
                .type(Notification.NotificationType.ANNOUNCEMENT)
                .recipientRole("SECURITY")
                .targetAdminId(guardId)   // reused as targetGuardId for SECURITY role
                .residentName(guard.getName())
                .isRead(false)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Message sent", notifRepo.save(notif)));
    }

    // ══════════════════════════════════════════════════════════════════════
    // SECURITY endpoints  (logged-in security guard)
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/security/residents")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<List<SecurityResidentDTO>>> getResidents() {
        return ResponseEntity.ok(ApiResponse.success(securityService.getResidentsList()));
    }

    @GetMapping("/security/notifications")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<List<Notification>>> getMyNotifications(
            Authentication auth) {
        SecurityGuard guard = resolveGuard(auth);
        List<Notification> notifs =
                notifRepo.findSecurityNotificationsForGuard(guard.getId());
        return ResponseEntity.ok(ApiResponse.success(notifs));
    }

    @GetMapping("/security/notifications/unread-count")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            Authentication auth) {
        SecurityGuard guard = resolveGuard(auth);
        long count = notifRepo.countUnreadSecurityNotifications(guard.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PutMapping("/security/notifications/{id}/read")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable Long id) {
        Notification n = notifRepo.findById(id)
                .orElseThrow(() -> new CustomException(
                        "Notification not found", HttpStatus.NOT_FOUND));
        n.setRead(true);
        return ResponseEntity.ok(ApiResponse.success("Marked read", notifRepo.save(n)));
    }

    /** Security guard sends a message to Admin. */
    @PostMapping("/security/messages")
    @PreAuthorize("hasRole('SECURITY')")
    public ResponseEntity<ApiResponse<Notification>> sendMessageToAdmin(
            @RequestBody Map<String, String> body, Authentication auth) {

        SecurityGuard guard = resolveGuard(auth);
        String title   = body.getOrDefault("title", "Message from Security");
        String message = body.getOrDefault("message", "");
        if (message.isBlank())
            throw new CustomException("Message cannot be empty", HttpStatus.BAD_REQUEST);

        Notification notif = Notification.builder()
                .title(title)
                .message("[Security – " + guard.getName() + "]: " + message)
                .type(Notification.NotificationType.ANNOUNCEMENT)
                .recipientRole("ADMIN")
                .residentName(guard.getName())
                .isRead(false)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Message sent", notifRepo.save(notif)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Enforces Super Admin / President access.
     * President accounts have superAdmin=true in the DB (set by AdminAssignmentService.appoint),
     * so isSuperAdmin() returns true for both the system Super Admin and the appointed President.
     */
    private void requireSuperAdmin(Authentication auth) {
        Admin admin = adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        boolean isSa = false;
        try { isSa = admin.isSuperAdmin(); } catch (Exception ignored) {}
        if (!isSa)
            throw new CustomException(
                    "Only Super Admin / President can manage security accounts",
                    HttpStatus.FORBIDDEN);
    }

    private SecurityGuard resolveGuard(Authentication auth) {
        return guardRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException(
                        "Security account not found", HttpStatus.UNAUTHORIZED));
    }
}