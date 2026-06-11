package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.*;
import com.resitrack.service.ComplaintService;
import com.resitrack.service.NotificationService;
import com.resitrack.service.PaymentService;
import com.resitrack.service.ResidentService;
import com.resitrack.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifService;
    private final ResidentService     residentService;
    private final ComplaintService    complaintService;
    private final PaymentService      paymentService;
    private final AdminRepository     adminRepo;

    @GetMapping("/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> getAdminNotifs(Authentication auth) {
        Admin admin = adminRepo.findByEmail(auth.getName()).orElse(null);
        if (admin == null) {
            // Fallback: return all (should not happen in a valid session)
            return ResponseEntity.ok(notifService.getAllForAdmin());
        }
        return ResponseEntity.ok(notifService.getAllForAdmin(admin.getId(), admin.isSuperAdmin()));
    }

    @GetMapping("/admin/notifications/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication auth) {
        Admin admin = adminRepo.findByEmail(auth.getName()).orElse(null);
        long count;
        if (admin == null) {
            count = notifService.getAdminUnreadCount();
        } else {
            count = notifService.getAdminUnreadCount(admin.getId(), admin.isSuperAdmin());
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PostMapping("/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> createNotif(
            @RequestBody NotificationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Sent", notifService.create(req)));
    }

    @PutMapping("/admin/notifications/{id}/read")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Marked read", notifService.markRead(id)));
    }

    @DeleteMapping("/admin/notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAdminNotif(@PathVariable Long id) {
        notifService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/admin/notifications/{notifId}/approve-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> approvePaymentFromNotif(
            @PathVariable Long notifId) {
        PaymentResponseDTO result = notifService.approvePaymentFromNotification(notifId);
        return ResponseEntity.ok(ApiResponse.success("Payment approved and verified", result));
    }

    @PostMapping("/admin/notifications/{notifId}/reject-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> rejectPaymentFromNotif(
            @PathVariable Long notifId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", null) : null;
        PaymentResponseDTO result = notifService.rejectPaymentFromNotification(notifId, reason);
        return ResponseEntity.ok(ApiResponse.success("Payment rejected", result));
    }

    @PostMapping("/admin/pending-dues/{residentId}/notify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendReminder(@PathVariable Long residentId) {
        notifService.sendDueReminder(residentId);
        return ResponseEntity.ok(ApiResponse.success("Reminder sent", null));
    }

    @GetMapping("/user/notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Notification>> getUserNotifs(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(notifService.getForResident(r.getId()));
    }

    @GetMapping("/user/notifications/unread-count")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserUnreadCount(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notifService.getResidentUnreadCount(r.getId()))));
    }

    @PutMapping("/user/notifications/{id}/read")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Notification>> userMarkRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Marked read", notifService.markRead(id)));
    }

    @DeleteMapping("/user/notifications/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteUserNotif(@PathVariable Long id) {
        notifService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/user/notifications/complaint")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ComplaintResponseDTO>> sendComplaintLegacy(
            @RequestBody Map<String, String> body, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        ComplaintRequest req = new ComplaintRequest();
        req.setTitle(body.getOrDefault("title",       body.getOrDefault("subject", "")));
        req.setDescription(body.getOrDefault("description", body.getOrDefault("message", "")));
        ComplaintResponseDTO result = complaintService.submitComplaint(r.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Complaint submitted", result));
    }
}