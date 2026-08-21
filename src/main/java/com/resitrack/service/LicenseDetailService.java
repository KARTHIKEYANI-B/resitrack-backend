package com.resitrack.service;

import com.resitrack.dto.LicenseDetailDTO;
import com.resitrack.entity.LicenseDetail;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.LicenseDetailRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Licenses for the currently authenticated resident (owner or family
 * member — personal, not property-scoped). A resident may hold any number of
 * licenses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseDetailService {

    private final LicenseDetailRepository repo;
    private final ResidentRepository      residentRepo;

    public List<LicenseDetailDTO.Response> getMine(Long residentId) {
        return repo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)
                .stream()
                .map(LicenseDetailDTO.Response::from)
                .toList();
    }

    public LicenseDetailDTO.Response getById(Long id, Long residentId) {
        return LicenseDetailDTO.Response.from(findAndVerifyOwner(id, residentId));
    }

    @Transactional
    public LicenseDetailDTO.Response add(Long residentId, LicenseDetailDTO.Request req) {
        validateDates(req);
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        LicenseDetail e = LicenseDetail.builder()
                .resident(owner)
                .licenseType(req.getLicenseType().trim())
                .licenseNumber(req.getLicenseNumber().trim())
                .holderName(blankToNull(req.getHolderName()))
                .issueDate(req.getIssueDate())
                .expiryDate(req.getExpiryDate())
                .vehicleClasses(blankToNull(req.getVehicleClasses()))
                .issuingAuthority(blankToNull(req.getIssuingAuthority()))
                .state(blankToNull(req.getState()))
                .status(req.getStatus() == null || req.getStatus().isBlank() ? "Active" : req.getStatus().trim())
                .active(true)
                .build();

        LicenseDetail saved = repo.save(e);
        log.info("License added: {} for resident {}", saved.getLicenseNumber(), residentId);
        return LicenseDetailDTO.Response.from(saved);
    }

    @Transactional
    public LicenseDetailDTO.Response update(Long id, Long residentId, LicenseDetailDTO.Request req) {
        validateDates(req);
        LicenseDetail e = findAndVerifyOwner(id, residentId);

        e.setLicenseType(req.getLicenseType().trim());
        e.setLicenseNumber(req.getLicenseNumber().trim());
        e.setHolderName(blankToNull(req.getHolderName()));
        e.setIssueDate(req.getIssueDate());
        e.setExpiryDate(req.getExpiryDate());
        e.setVehicleClasses(blankToNull(req.getVehicleClasses()));
        e.setIssuingAuthority(blankToNull(req.getIssuingAuthority()));
        e.setState(blankToNull(req.getState()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) e.setStatus(req.getStatus().trim());

        LicenseDetail saved = repo.save(e);
        log.info("License updated: {}", saved.getId());
        return LicenseDetailDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        LicenseDetail e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("License soft-deleted: {}", id);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private LicenseDetail findAndVerifyOwner(Long id, Long residentId) {
        LicenseDetail e = repo.findById(id)
                .orElseThrow(() -> new CustomException("License not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this license does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("License has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private void validateDates(LicenseDetailDTO.Request req) {
        if (req.getIssueDate() != null && req.getExpiryDate() != null
                && req.getIssueDate().isAfter(req.getExpiryDate())) {
            throw new CustomException("Issue date must not be after expiry date", HttpStatus.BAD_REQUEST);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
