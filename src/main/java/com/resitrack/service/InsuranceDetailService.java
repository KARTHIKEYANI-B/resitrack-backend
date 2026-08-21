package com.resitrack.service;

import com.resitrack.dto.InsuranceDetailDTO;
import com.resitrack.entity.InsuranceDetail;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.InsuranceDetailRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Insurance policies for the currently authenticated resident
 * (owner or family member — insurance is personal, not property-scoped).
 * A resident may hold any number of policies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceDetailService {

    private final InsuranceDetailRepository repo;
    private final ResidentRepository        residentRepo;

    public List<InsuranceDetailDTO.Response> getMine(Long residentId) {
        return repo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)
                .stream()
                .map(InsuranceDetailDTO.Response::from)
                .toList();
    }

    public InsuranceDetailDTO.Response getById(Long id, Long residentId) {
        return InsuranceDetailDTO.Response.from(findAndVerifyOwner(id, residentId));
    }

    @Transactional
    public InsuranceDetailDTO.Response add(Long residentId, InsuranceDetailDTO.Request req) {
        validateDates(req);
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        InsuranceDetail e = InsuranceDetail.builder()
                .resident(owner)
                .insuranceType(req.getInsuranceType().trim())
                .provider(blankToNull(req.getProvider()))
                .policyNumber(req.getPolicyNumber().trim())
                .policyHolderName(blankToNull(req.getPolicyHolderName()))
                .insuredPersonName(blankToNull(req.getInsuredPersonName()))
                .startDate(req.getStartDate())
                .expiryDate(req.getExpiryDate())
                .premiumAmount(req.getPremiumAmount())
                .premiumFrequency(blankToNull(req.getPremiumFrequency()))
                .sumInsured(req.getSumInsured())
                .nomineeName(blankToNull(req.getNomineeName()))
                .nomineeRelationship(blankToNull(req.getNomineeRelationship()))
                .nomineeContact(blankToNull(req.getNomineeContact()))
                .status(req.getStatus() == null || req.getStatus().isBlank() ? "Active" : req.getStatus().trim())
                .active(true)
                .build();

        InsuranceDetail saved = repo.save(e);
        log.info("Insurance policy added: {} for resident {}", saved.getPolicyNumber(), residentId);
        return InsuranceDetailDTO.Response.from(saved);
    }

    @Transactional
    public InsuranceDetailDTO.Response update(Long id, Long residentId, InsuranceDetailDTO.Request req) {
        validateDates(req);
        InsuranceDetail e = findAndVerifyOwner(id, residentId);

        e.setInsuranceType(req.getInsuranceType().trim());
        e.setProvider(blankToNull(req.getProvider()));
        e.setPolicyNumber(req.getPolicyNumber().trim());
        e.setPolicyHolderName(blankToNull(req.getPolicyHolderName()));
        e.setInsuredPersonName(blankToNull(req.getInsuredPersonName()));
        e.setStartDate(req.getStartDate());
        e.setExpiryDate(req.getExpiryDate());
        e.setPremiumAmount(req.getPremiumAmount());
        e.setPremiumFrequency(blankToNull(req.getPremiumFrequency()));
        e.setSumInsured(req.getSumInsured());
        e.setNomineeName(blankToNull(req.getNomineeName()));
        e.setNomineeRelationship(blankToNull(req.getNomineeRelationship()));
        e.setNomineeContact(blankToNull(req.getNomineeContact()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) e.setStatus(req.getStatus().trim());

        InsuranceDetail saved = repo.save(e);
        log.info("Insurance policy updated: {}", saved.getId());
        return InsuranceDetailDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        InsuranceDetail e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("Insurance policy soft-deleted: {}", id);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private InsuranceDetail findAndVerifyOwner(Long id, Long residentId) {
        InsuranceDetail e = repo.findById(id)
                .orElseThrow(() -> new CustomException("Insurance policy not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this insurance policy does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Insurance policy has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private void validateDates(InsuranceDetailDTO.Request req) {
        if (req.getStartDate() != null && req.getExpiryDate() != null
                && req.getStartDate().isAfter(req.getExpiryDate())) {
            throw new CustomException("Start date must not be after expiry date", HttpStatus.BAD_REQUEST);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
