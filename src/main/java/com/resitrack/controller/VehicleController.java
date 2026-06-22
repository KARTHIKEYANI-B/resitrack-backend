package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.VehicleDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ResidentService;
import com.resitrack.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Owner-facing endpoints for managing multiple vehicles and their
 * insurance documents. Mounted under /user/vehicles, protected by the
 * existing ROLE_USER security matcher ("/user/**") — no SecurityConfig
 * changes are required.
 */
@RestController
@RequestMapping("/user/vehicles")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService  vehicleService;
    private final ResidentService residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleDTO.Response>>> getMyVehicles(Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getMyVehicles(owner.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> getVehicle(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getById(id, owner.getId())));
    }

    // ── Add vehicle — JSON only (no insurance document) ───────────────────
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> addVehicle(
            @RequestBody VehicleDTO.Request req, Authentication auth) {
        Resident owner = getOwner(auth);
        VehicleDTO.Response created = vehicleService.addVehicle(owner.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle added successfully", created));
    }

    // ── Add vehicle — multipart, with optional insurance document ─────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> addVehicleWithDocument(
            @RequestParam("vehicleNumber") String vehicleNumber,
            @RequestParam(value = "vehicleType", required = false) String vehicleType,
            @RequestParam(value = "insuranceNumber", required = false) String insuranceNumber,
            @RequestParam(value = "insuranceProvider", required = false) String insuranceProvider,
            @RequestParam(value = "insuranceExpiryDate", required = false) String insuranceExpiryDate,
            @RequestParam(value = "insuranceDocument", required = false) MultipartFile insuranceDocument,
            Authentication auth) {

        Resident owner = getOwner(auth);

        VehicleDTO.Request req = new VehicleDTO.Request();
        req.setVehicleNumber(vehicleNumber);
        req.setVehicleType(vehicleType);
        req.setInsuranceNumber(insuranceNumber);
        req.setInsuranceProvider(insuranceProvider);
        req.setInsuranceExpiryDate(parseDate(insuranceExpiryDate));

        VehicleDTO.Response created =
                vehicleService.addVehicleWithDocument(owner.getId(), req, insuranceDocument);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle added successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> updateVehicle(
            @PathVariable Long id, @RequestBody VehicleDTO.Request req, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("Vehicle updated",
                vehicleService.updateVehicle(id, owner.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeVehicle(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        vehicleService.removeVehicle(id, owner.getId());
        return ResponseEntity.ok(ApiResponse.success("Vehicle removed", null));
    }

    // ── Insurance document upload / replace ────────────────────────────────
    @PostMapping(value = "/{id}/insurance-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> uploadInsuranceDocument(
            @PathVariable Long id,
            @RequestPart("insuranceDocument") MultipartFile insuranceDocument,
            Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("Insurance document uploaded",
                vehicleService.uploadInsuranceDocument(id, owner.getId(), insuranceDocument)));
    }

    @DeleteMapping("/{id}/insurance-document")
    public ResponseEntity<ApiResponse<VehicleDTO.Response>> removeInsuranceDocument(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("Insurance document removed",
                vehicleService.removeInsuranceDocument(id, owner.getId())));
    }

    private java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private Resident getOwner(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        if (r.getResidentRole() != Resident.ResidentRole.OWNER) {
            throw new com.resitrack.exception.CustomException(
                    "Only property owners can manage vehicles",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return r;
    }
}
