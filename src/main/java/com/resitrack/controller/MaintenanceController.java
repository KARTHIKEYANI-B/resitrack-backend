package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.entity.Maintenance;
import com.resitrack.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<List<Maintenance>> getAll() {
        return ResponseEntity.ok(maintenanceService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Maintenance>> create(@RequestBody MaintenanceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Created", maintenanceService.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Maintenance>> update(
            @PathVariable Long id, @RequestBody MaintenanceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", maintenanceService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        maintenanceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @GetMapping("/owner-list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MaintenanceListDTO>> getOwnerMaintenanceList(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        int y = (year  != null && year  > 0) ? year  : LocalDate.now().getYear();
        int m = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(maintenanceService.getOwnerMaintenanceList(y, m)));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllBatches() {
        return ResponseEntity.ok(ApiResponse.success(maintenanceService.getAllBatches()));
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(maintenanceService.getBatchById(id)));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBatch(
            @RequestBody MaintenanceBatchRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Batch created", maintenanceService.createBatch(req)));
    }

    @PutMapping("/batches/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> updateBatchStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                maintenanceService.updateBatchStatus(id, status)));
    }

    @DeleteMapping("/batches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable Long id) {
        maintenanceService.deleteBatch(id);
        return ResponseEntity.ok(ApiResponse.success("Batch deleted", null));
    }
}