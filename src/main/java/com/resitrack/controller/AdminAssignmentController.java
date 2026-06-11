package com.resitrack.controller;

import com.resitrack.dto.AdminAssignmentDTO;
import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.AdminAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/assignments")
@RequiredArgsConstructor
public class AdminAssignmentController {

    private final AdminAssignmentService assignmentService;
    private final AdminRepository        adminRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAssignmentDTO.Response>>> getActiveAssignments() {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getActiveAssignments()));
    }

    @GetMapping("/resident/{id}")
    public ResponseEntity<ApiResponse<List<AdminAssignmentDTO.Response>>> getResidentHistory(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getAssignmentHistory(id)));
    }

    @GetMapping("/position/{position}")
    public ResponseEntity<ApiResponse<List<AdminAssignmentDTO.Response>>> getPositionHistory(
            @PathVariable String position) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getPositionHistory(position)));
    }

    @PostMapping("/appoint")
    public ResponseEntity<ApiResponse<AdminAssignmentDTO.AppointResponse>> appoint(
            @RequestBody AdminAssignmentDTO.AppointRequest req,
            Authentication auth) {
        requireSuperAdmin(auth);
        AdminAssignmentDTO.AppointResponse result = assignmentService.appoint(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment successful", result));
    }

    @PostMapping("/revoke")
    public ResponseEntity<ApiResponse<AdminAssignmentDTO.Response>> revoke(
            @RequestBody AdminAssignmentDTO.RevokeRequest req,
            Authentication auth) {
        requireSuperAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success("Assignment revoked",
                assignmentService.revoke(req)));
    }

    private void requireSuperAdmin(Authentication auth) {
        String email = auth.getName();
        Admin admin = adminRepo.findByEmail(email)
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        try {
            if (!admin.isSuperAdmin()) {
                throw new CustomException(
                        "Only SUPER_ADMIN can manage admin assignments", HttpStatus.FORBIDDEN);
            }
        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            throw new CustomException("Only SUPER_ADMIN can manage admin assignments",
                    HttpStatus.FORBIDDEN);
        }
    }
}