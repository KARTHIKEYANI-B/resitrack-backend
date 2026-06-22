package com.resitrack.service;

import com.resitrack.dto.ResidentDTO;
import com.resitrack.dto.FamilyMemberSummaryDTO;
import com.resitrack.dto.VehicleDTO;
import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Resident;
import com.resitrack.entity.FamilyMember;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.repository.PaymentRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResidentService {

    private final ResidentRepository      residentRepo;
    private final PaymentRepository       paymentRepo;
    private final PhotoUploadService      photoUploadService;
    private final FamilyMemberRepository  familyMemberRepo;
    private final VehicleRepository       vehicleRepo;
    private final VehicleDocumentUploadService vehicleDocumentUploadService;

    // ── List queries ──────────────────────────────────────────────────────

    /**
     * Returns only OWNER rows that are approved, active, and not deleted.
     */
    public List<ResidentDTO> getAllResidents() {
        return residentRepo.findAllActiveApprovedOwners().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Active + approved OWNER residents only.
     */
    public List<ResidentDTO> getActiveResidents() {
        return residentRepo.findAllActiveNonDeleted().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Update resident details (admin) ───────────────────────────────────

    public ResidentDTO updateResident(Long id, ResidentDTO dto) {
        Resident r = residentRepo.findById(id)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        if (dto.getFullName()       != null) r.setFullName(dto.getFullName());
        if (dto.getPhone()          != null) r.setPhone(dto.getPhone());
        if (dto.getSquareFeet()     != null) r.setSqFt(dto.getSquareFeet());
        if (dto.getEmail()          != null) {
            r.setAdminEmail(dto.getEmail());
            if (!r.isRegistered()) r.setEmail(null);
        }
        if (dto.getFlatNumber()     != null) r.setFlatNumber(dto.getFlatNumber());
        if (dto.getFamilyMembers()  != null) r.setFamilyMembers(dto.getFamilyMembers());
        if (dto.getAge()            != null) r.setAge(dto.getAge());
        if (dto.getAddress()        != null) r.setAddress(dto.getAddress());
        if (dto.getVehicleDetails() != null) r.setVehicleDetails(dto.getVehicleDetails());

        if (dto.getPropertyType() != null) {
            try {
                r.setPropertyType(PropertyType.valueOf(dto.getPropertyType()));
            } catch (IllegalArgumentException ignored) {}
        }

        return toDTO(residentRepo.save(r));
    }

    // ── Owner self-update (profile tab including insurance + taxes) ───────

    @Transactional
    public ResidentDTO updateOwnerProfile(Long residentId, ResidentDTO dto) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        if (dto.getFullName()       != null) r.setFullName(dto.getFullName());
        if (dto.getPhone()          != null) r.setPhone(dto.getPhone());
        if (dto.getAddress()        != null) r.setAddress(dto.getAddress());
        if (dto.getVehicleDetails() != null) r.setVehicleDetails(dto.getVehicleDetails());

        // ── Vehicle Insurance ─────────────────────────────────────────────
        // Allow explicit null to clear fields
        r.setInsuranceProvider(dto.getInsuranceProvider());
        r.setInsuranceNumber(dto.getInsuranceNumber());
        r.setInsuranceExpiryDate(dto.getInsuranceExpiryDate());

        // ── Taxes Reminder ────────────────────────────────────────────────
        r.setTaxesReminderEnabled(dto.isTaxesReminderEnabled());
        r.setEbReferenceNumber(dto.getEbReferenceNumber());
        r.setEbDueDate(dto.getEbDueDate());
        r.setWaterTaxReferenceNumber(dto.getWaterTaxReferenceNumber());
        r.setWaterTaxDueDate(dto.getWaterTaxDueDate());
        r.setBuildingTaxReferenceNumber(dto.getBuildingTaxReferenceNumber());
        r.setBuildingTaxDueDate(dto.getBuildingTaxDueDate());
        r.setTaxesInsuranceReferenceNumber(dto.getTaxesInsuranceReferenceNumber());
        r.setTaxesInsuranceDueDate(dto.getTaxesInsuranceDueDate());

        return toDTO(residentRepo.save(r));
    }

    // ── Profile photo management ──────────────────────────────────────────

    @Transactional
    public ResidentDTO updateProfilePhoto(Long residentId, MultipartFile file) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        if (r.getProfilePhoto() != null) {
            photoUploadService.deletePhoto(r.getProfilePhoto());
        }

        String relativePath = photoUploadService.saveProfilePhoto(file);
        r.setProfilePhoto(relativePath);
        return toDTO(residentRepo.save(r));
    }

    @Transactional
    public ResidentDTO removeProfilePhoto(Long residentId) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        if (r.getProfilePhoto() != null) {
            photoUploadService.deletePhoto(r.getProfilePhoto());
            r.setProfilePhoto(null);
            residentRepo.save(r);
        }
        return toDTO(r);
    }

    // ── Delete resident ───────────────────────────────────────────────────

    @Transactional
    public void deleteResident(Long id) {
        Resident r = residentRepo.findById(id)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        if (r.getProfilePhoto() != null) {
            photoUploadService.deletePhoto(r.getProfilePhoto());
        }

        r.setPassword("__DELETED__");
        r.setEmail(null);
        r.setPhone(null);
        r.setFlatNumber("DEL-" + id);
        r.setRegisterNumber(null);
        r.setProfilePhoto(null);
        r.setActive(false);
        r.setApproved(false);
        r.setRegistered(false);
        r.setStatus(Resident.ResidentStatus.INACTIVE);
        r.setRegistrationStatus(Resident.RegistrationStatus.REJECTED);
        r.setRejectedReason("PERMANENTLY_DELETED_BY_ADMIN");

        residentRepo.save(r);
    }

    // ── Family members for a specific owner (admin view) ──────────────────

    public List<FamilyMemberSummaryDTO> getFamilyMembersForOwners(Long ownerId) {
        return familyMemberRepo.findByResidentIdAndActiveTrue(ownerId)
                .stream()
                .map(fm -> {
                    String loginEmail = null;
                    if (fm.getUserId() != null) {
                        loginEmail = residentRepo.findById(fm.getUserId())
                                .map(Resident::getEmail)
                                .orElse(null);
                    }
                    return FamilyMemberSummaryDTO.from(fm, loginEmail);
                })
                .collect(Collectors.toList());
    }


    // ── Family members within an age range (Population age-range filter) ───

    public List<FamilyMemberSummaryDTO> getFamilyMembersByAgeRange(int minAge, int maxAge) {
        return familyMemberRepo.findByAgeRange(minAge, maxAge)
                .stream()
                .map(fm -> {
                    String loginEmail = null;
                    if (fm.getUserId() != null) {
                        loginEmail = residentRepo.findById(fm.getUserId())
                                .map(Resident::getEmail)
                                .orElse(null);
                    }
                    return FamilyMemberSummaryDTO.from(fm, loginEmail);
                })
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns the effective OWNER Resident for any authenticated user.
     *
     * OWNER         -> returns r unchanged.
     * FAMILY_MEMBER -> follows ownerResidentId to the linked Owner Resident row.
     *
     * This is the single routing point used by all payment and dues endpoints.
     * Maintenance obligations belong to the Owner's property, not the FM account,
     * so every query that computes paid/pending amounts must use the owner's ID.
     *
     * Safe: if residentRole is null (legacy row) the method returns r unchanged.
     */
    public Resident getEffectiveOwnerResident(Resident r) {
        try {
            if (r.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER) {
                Long ownerId = r.getOwnerResidentId();
                if (ownerId != null) {
                    return residentRepo.findById(ownerId).orElse(r);
                }
            }
        } catch (Exception ignored) { /* legacy rows with null residentRole */ }
        return r;
    }

    public Resident getByEmail(String email) {
        return residentRepo.findByEmail(email)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
    }

    public ResidentDTO toDTO(Resident r) {
        return ResidentDTO.builder()
                .id(r.getId())
                .fullName(r.getFullName())
                .email(r.getEmail() != null ? r.getEmail() : r.getAdminEmail())
                .phone(r.getPhone())
                .flatNumber(r.getFlatNumber())
                .flatType(r.getFlatType())
                .propertyType(r.getPropertyType() != null ? r.getPropertyType().name() : null)
                .squareFeet(r.getSqFt())
                .familyMembers(r.getFamilyMembers())
                .age(r.getAge())
                .vehicleDetails(r.getVehicleDetails())
                .address(r.getAddress())
                .status(r.getStatus().name())
                .registrationStatus(r.getRegistrationStatus() != null
                        ? r.getRegistrationStatus().name() : null)
                .isApproved(r.isApproved())
                .registered(r.isRegistered())
                .createdAt(r.getCreatedAt())
                .profilePhotoUrl(photoUploadService.toPublicUrl(r.getProfilePhoto()))
                // ── Vehicle Insurance ─────────────────────────────────────
                .insuranceProvider(r.getInsuranceProvider())
                .insuranceNumber(r.getInsuranceNumber())
                .insuranceExpiryDate(r.getInsuranceExpiryDate())
                // ── Taxes Reminder ────────────────────────────────────────
                .taxesReminderEnabled(r.isTaxesReminderEnabled())
                .ebReferenceNumber(r.getEbReferenceNumber())
                .ebDueDate(r.getEbDueDate())
                .waterTaxReferenceNumber(r.getWaterTaxReferenceNumber())
                .waterTaxDueDate(r.getWaterTaxDueDate())
                .buildingTaxReferenceNumber(r.getBuildingTaxReferenceNumber())
                .buildingTaxDueDate(r.getBuildingTaxDueDate())
                .taxesInsuranceReferenceNumber(r.getTaxesInsuranceReferenceNumber())
                .taxesInsuranceDueDate(r.getTaxesInsuranceDueDate())
                // ── Multiple Vehicles ──────────────────────────────────────
                .vehicles(getVehiclesForResident(r.getId()))
                .build();
    }

    private List<VehicleDTO.Response> getVehiclesForResident(Long residentId) {
        return vehicleRepo.findByResidentIdAndActiveTrueOrderByCreatedAtAsc(residentId)
                .stream()
                .map(v -> VehicleDTO.Response.from(v,
                        vehicleDocumentUploadService.toPublicUrl(v.getInsuranceDocumentPath())))
                .collect(Collectors.toList());
    }
}