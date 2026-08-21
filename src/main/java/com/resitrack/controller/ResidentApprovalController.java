package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Resident;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.ResidentApprovalService;
import com.resitrack.util.ViewerGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/approvals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ResidentApprovalController {

    private final ResidentApprovalService approvalService;
    private final AdminRepository         adminRepo;
    private final ViewerGuard             viewerGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Resident>>> getAll(
            @RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getAllRegistrations(status)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("pending", approvalService.getPendingCount())));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Resident>> approve(
            @PathVariable Long id, Authentication auth) {
        viewerGuard.rejectViewer(auth);
        Long adminId = getAdminId(auth);
        return ResponseEntity.ok(ApiResponse.success("Resident approved",
                approvalService.approve(id, adminId)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Resident>> reject(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        viewerGuard.rejectViewer(auth);
        Long adminId = getAdminId(auth);
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success("Resident rejected",
                approvalService.reject(id, adminId, reason)));
    }

    @PutMapping("/bulk-approve")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkApprove(
            @RequestBody Map<String, List<Long>> body, Authentication auth) {
        viewerGuard.rejectViewer(auth);
        Long adminId = getAdminId(auth);
        List<Long> ids = body.get("ids");
        int count = approvalService.bulkApprove(ids, adminId);
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk approved " + count + " residents", Map.of("approved", count)));
    }

    @PutMapping("/bulk-reject")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkReject(
            @RequestBody Map<String, Object> body, Authentication auth) {
        viewerGuard.rejectViewer(auth);
        Long adminId = getAdminId(auth);
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        String reason  = (String) body.get("reason");
        int count = approvalService.bulkReject(ids, adminId, reason);
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk rejected " + count + " residents", Map.of("rejected", count)));
    }

    private Long getAdminId(Authentication auth) {
        return adminRepo.findByEmail(auth.getName())
                .map(Admin::getId).orElse(0L);
    }
}
