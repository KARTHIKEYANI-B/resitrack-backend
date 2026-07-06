package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Notification;
import com.resitrack.entity.Payment;
import com.resitrack.entity.Resident;
import com.resitrack.entity.SecurityGuard;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.PaymentVerificationRequestRepository;
import com.resitrack.repository.SecurityGuardRepository;
import com.resitrack.service.DashboardService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/user")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class UserController {

    private final DashboardService                      dashboardService;
    private final ResidentService                       residentService;
    private final MaintenanceRepository                 maintenanceRepo;
    private final PaymentRepository                     paymentRepo;
    private final SecurityGuardRepository               guardRepo;
    private final NotificationRepository                notifRepo;
    private final PaymentVerificationRequestRepository  verificationRepo;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getUserStats(owner.getId())));
    }

    @GetMapping("/maintenance/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentMaintenance(Authentication auth) {
        Resident raw      = residentService.getByEmail(auth.getName());
        Resident resident = residentService.getEffectiveOwnerResident(raw);

        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        String currentMonthStr = year + "-" + String.format("%02d", month);

        Map<String, Object> stats = dashboardService.getUserStats(resident.getId());

        Optional<Maintenance> activeMaint = maintenanceRepo.findFirstByActiveOrderByCreatedAtDesc(true);

        Map<String, Object> response = new LinkedHashMap<>();

        double amount         = toDouble(stats.get("currentMonthDue"));
        double lateFee        = toDouble(stats.get("lateFee"));
        boolean lateFeeApplied = Boolean.TRUE.equals(stats.get("lateFeeApplied"));
        String  dueDate        = (String) stats.get("dueDate");
        String  status         = (String) stats.get("paymentStatus"); // PAID | PENDING_VERIFICATION | UNPAID
        String  txnId          = (String) stats.get("transactionId");

        response.put("maintenanceId",   activeMaint.map(Maintenance::getId).orElse(null));
        response.put("amount",          amount);
        response.put("dueDate",         dueDate);
        response.put("lateFee",         lateFee);
        response.put("lateFeeEnabled",  activeMaint.map(Maintenance::getLateFeeEnabled).orElse(false));
        response.put("lateFeeApplied",  lateFeeApplied);
        response.put("lateFeeAmount",   lateFee);
        response.put("status",          status != null ? status : "UNPAID");
        response.put("month",           formatMonthLabel(currentMonthStr));
        response.put("paymentMonth",    currentMonthStr);
        response.put("flatType",        resident.getFlatType());
        response.put("transactionId",   txnId);
        response.put("paidAmount",      toDouble(stats.get("paidAmount")));
        response.put("pendingAmount",   toDouble(stats.get("currentDue")));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return 0.0; }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ResidentDTO>> getProfile(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(residentService.toDTO(r)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ResidentDTO>> updateProfile(
            @RequestBody ResidentDTO dto, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                residentService.updateResident(r.getId(), dto)));
    }

    @PutMapping("/profile/full")
    public ResponseEntity<ApiResponse<ResidentDTO>> updateFullProfile(
            @RequestBody ResidentDTO dto, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                residentService.updateOwnerProfile(r.getId(), dto)));
    }

    private String formatMonthLabel(String yearMonth) {
        if (yearMonth == null) return "";
        try {
            String[] parts = yearMonth.split("-");
            int yr = Integer.parseInt(parts[0]);
            int mo = Integer.parseInt(parts[1]);
            String[] MONTHS = {"","January","February","March","April","May","June",
                               "July","August","September","October","November","December"};
            return MONTHS[mo] + " " + yr;
        } catch (Exception e) {
            return yearMonth;
        }
    }

    @GetMapping("/security/guards")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSecurityGuards() {
        List<Map<String, Object>> result = guardRepo.findAll().stream()
                .filter(SecurityGuard::isActive)
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",    g.getId());
                    m.put("name",  g.getName());
                    m.put("phone", g.getPhone());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/security/{guardId}/message")
    public ResponseEntity<ApiResponse<Notification>> sendMessageToSecurity(
            @PathVariable Long guardId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Resident resident = residentService.getByEmail(auth.getName());

        SecurityGuard guard = guardRepo.findById(guardId)
                .orElseThrow(() -> new CustomException(
                        "Security account not found", HttpStatus.NOT_FOUND));

        String title   = body.getOrDefault("title", "Message from Resident");
        String message = body.getOrDefault("message", "");
        if (message.isBlank())
            throw new CustomException("Message cannot be empty", HttpStatus.BAD_REQUEST);

        String senderLabel = resident.getFullName() != null
                ? resident.getFullName()
                : auth.getName();

        Notification notif = Notification.builder()
                .title(title.isBlank() ? "Message from " + senderLabel : title)
                .message("[" + senderLabel + "]: " + message)
                .type(Notification.NotificationType.ANNOUNCEMENT)
                .recipientRole("SECURITY")
                .targetAdminId(guard.getId())   
                .residentName(guard.getName())
                .isRead(false)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Message sent", notifRepo.save(notif)));
    }
}