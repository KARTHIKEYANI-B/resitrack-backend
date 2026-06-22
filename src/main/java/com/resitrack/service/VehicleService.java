package com.resitrack.service;

import com.resitrack.dto.VehicleDTO;
import com.resitrack.entity.Resident;
import com.resitrack.entity.Vehicle;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages Vehicle records for Owner residents.
 *
 * An owner may have any number of vehicles; each vehicle may optionally
 * carry its own insurance document (image or PDF) plus insurance number
 * and expiry date. This service does not touch the legacy single-vehicle
 * fields on Resident (vehicleDetails / insuranceNumber / insuranceExpiryDate),
 * which remain fully functional and untouched elsewhere in the codebase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository            vehicleRepo;
    private final ResidentRepository           residentRepo;
    private final VehicleDocumentUploadService documentUploadService;

    // ── Get all vehicles for the logged-in owner ───────────────────────────
    public List<VehicleDTO.Response> getMyVehicles(Long ownerId) {
        return vehicleRepo.findByResidentIdAndActiveTrueOrderByCreatedAtAsc(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get single vehicle (must belong to owner) ──────────────────────────
    public VehicleDTO.Response getById(Long vehicleId, Long ownerId) {
        Vehicle v = findAndVerifyOwner(vehicleId, ownerId);
        return toResponse(v);
    }

    // ── Add vehicle (metadata only, no file) ───────────────────────────────
    @Transactional
    public VehicleDTO.Response addVehicle(Long ownerId, VehicleDTO.Request req) {
        validate(req);
        Resident owner = residentRepo.findById(ownerId)
                .orElseThrow(() -> new CustomException("Owner not found", HttpStatus.NOT_FOUND));

        Vehicle vehicle = Vehicle.builder()
                .resident(owner)
                .vehicleNumber(req.getVehicleNumber().trim().toUpperCase())
                .vehicleType(req.getVehicleType())
                .insuranceNumber(req.getInsuranceNumber())
                .insuranceProvider(req.getInsuranceProvider())
                .insuranceExpiryDate(req.getInsuranceExpiryDate())
                .active(true)
                .build();

        Vehicle saved = vehicleRepo.save(vehicle);
        log.info("Vehicle added: {} for owner {}", saved.getVehicleNumber(), ownerId);
        return toResponse(saved);
    }

    // ── Add vehicle with an insurance document in the same call ───────────
    @Transactional
    public VehicleDTO.Response addVehicleWithDocument(Long ownerId, VehicleDTO.Request req,
                                                       MultipartFile insuranceDocument) {
        VehicleDTO.Response created = addVehicle(ownerId, req);
        if (insuranceDocument != null && !insuranceDocument.isEmpty()) {
            return uploadInsuranceDocument(created.getId(), ownerId, insuranceDocument);
        }
        return created;
    }

    // ── Update vehicle metadata ────────────────────────────────────────────
    @Transactional
    public VehicleDTO.Response updateVehicle(Long vehicleId, Long ownerId, VehicleDTO.Request req) {
        Vehicle v = findAndVerifyOwner(vehicleId, ownerId);

        if (req.getVehicleNumber() != null && !req.getVehicleNumber().isBlank()) {
            v.setVehicleNumber(req.getVehicleNumber().trim().toUpperCase());
        }
        if (req.getVehicleType() != null) v.setVehicleType(req.getVehicleType());
        if (req.getInsuranceNumber() != null) v.setInsuranceNumber(req.getInsuranceNumber());
        if (req.getInsuranceProvider() != null) v.setInsuranceProvider(req.getInsuranceProvider());
        if (req.getInsuranceExpiryDate() != null) v.setInsuranceExpiryDate(req.getInsuranceExpiryDate());

        Vehicle saved = vehicleRepo.save(v);
        log.info("Vehicle updated: {}", saved.getId());
        return toResponse(saved);
    }

    // ── Upload / replace insurance document for an existing vehicle ───────
    @Transactional
    public VehicleDTO.Response uploadInsuranceDocument(Long vehicleId, Long ownerId, MultipartFile file) {
        Vehicle v = findAndVerifyOwner(vehicleId, ownerId);

        if (v.getInsuranceDocumentPath() != null) {
            documentUploadService.deleteDocument(v.getInsuranceDocumentPath());
        }

        String relativePath = documentUploadService.saveInsuranceDocument(file);
        v.setInsuranceDocumentPath(relativePath);
        v.setInsuranceDocumentName(file.getOriginalFilename());

        Vehicle saved = vehicleRepo.save(v);
        log.info("Insurance document uploaded for vehicle {}", vehicleId);
        return toResponse(saved);
    }

    // ── Remove insurance document only (keep the vehicle record) ──────────
    @Transactional
    public VehicleDTO.Response removeInsuranceDocument(Long vehicleId, Long ownerId) {
        Vehicle v = findAndVerifyOwner(vehicleId, ownerId);

        if (v.getInsuranceDocumentPath() != null) {
            documentUploadService.deleteDocument(v.getInsuranceDocumentPath());
            v.setInsuranceDocumentPath(null);
            v.setInsuranceDocumentName(null);
            vehicleRepo.save(v);
        }
        return toResponse(v);
    }

    // ── Remove vehicle (soft delete, keeps history/audit trail) ───────────
    @Transactional
    public void removeVehicle(Long vehicleId, Long ownerId) {
        Vehicle v = findAndVerifyOwner(vehicleId, ownerId);
        v.setActive(false);
        vehicleRepo.save(v);
        log.info("Vehicle soft-deleted: {}", vehicleId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Vehicle findAndVerifyOwner(Long vehicleId, Long ownerId) {
        Vehicle v = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new CustomException("Vehicle not found", HttpStatus.NOT_FOUND));
        if (!v.getResident().getId().equals(ownerId)) {
            throw new CustomException("Access denied: this vehicle does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!v.isActive()) {
            throw new CustomException("Vehicle has been removed", HttpStatus.NOT_FOUND);
        }
        return v;
    }

    private VehicleDTO.Response toResponse(Vehicle v) {
        String url = documentUploadService.toPublicUrl(v.getInsuranceDocumentPath());
        return VehicleDTO.Response.from(v, url);
    }

    private void validate(VehicleDTO.Request req) {
        if (req.getVehicleNumber() == null || req.getVehicleNumber().isBlank()) {
            throw new CustomException("Vehicle number is required", HttpStatus.BAD_REQUEST);
        }
    }
}
