package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.ComplaintRequest;
import com.resitrack.dto.ComplaintResponseDTO;
import com.resitrack.entity.Complaint;
import com.resitrack.entity.Resident;
import com.resitrack.service.ComplaintService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final ResidentService  residentService;

    @PostMapping("/user/complaints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ComplaintResponseDTO>> submitComplaint(
            @RequestBody ComplaintRequest req, Authentication auth) {
        Resident resident = residentService.getByEmail(auth.getName());
        ComplaintResponseDTO result = complaintService.submitComplaint(resident.getId(), req);
        return ResponseEntity.ok(ApiResponse.success("Complaint submitted successfully", result));
    }

    @GetMapping("/user/complaints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<ComplaintResponseDTO>>> getMyComplaints(
            Authentication auth) {
        Resident resident = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.getComplaintsForResident(resident.getId())));
    }

    @GetMapping("/admin/complaints")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplaintResponseDTO>>> getAllComplaints(
            @RequestParam(required = false) String status) {
        List<ComplaintResponseDTO> list;
        if (status != null && !status.isBlank()) {
            try {
                list = complaintService.getComplaintsByStatus(
                        Complaint.ComplaintStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                list = complaintService.getAllComplaints();
            }
        } else {
            list = complaintService.getAllComplaints();
        }
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/admin/complaints/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getStats()));
    }

    @PutMapping("/admin/complaints/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponseDTO>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newStatus  = body.get("status");
        String adminReply = body.get("adminReply");
        ComplaintResponseDTO updated = complaintService.updateStatus(id, newStatus, adminReply);
        return ResponseEntity.ok(ApiResponse.success("Complaint updated", updated));
    }
}