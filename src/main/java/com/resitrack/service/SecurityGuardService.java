package com.resitrack.service;

import com.resitrack.dto.SecurityGuardDTO;
import com.resitrack.dto.SecurityResidentDTO;
import com.resitrack.entity.FamilyMember;
import com.resitrack.entity.Resident;
import com.resitrack.entity.SecurityGuard;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.SecurityGuardRepository;
import com.resitrack.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecurityGuardService {

    private final SecurityGuardRepository guardRepo;
    private final ResidentRepository      residentRepo;
    private final FamilyMemberRepository  familyMemberRepo;
    private final PasswordEncoder         passwordEncoder;

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public SecurityGuardDTO.Response create(SecurityGuardDTO.Request req, Long adminId) {
        if (req.getName() == null || req.getName().isBlank())
            throw new CustomException("Name is required", HttpStatus.BAD_REQUEST);
        if (req.getPassword() == null || req.getPassword().length() < 6)
            throw new CustomException("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);

        boolean hasEmail = req.getEmail() != null && !req.getEmail().isBlank();
        String normalizedPhone = PhoneNormalizer.normalize(req.getPhone());
        boolean hasPhone = normalizedPhone != null;
        if (!hasEmail && !hasPhone)
            throw new CustomException("Email or Phone is required", HttpStatus.BAD_REQUEST);

        String normalEmail = hasEmail
                ? req.getEmail().trim().toLowerCase()
                : "security." + normalizedPhone + "@resitrack.internal";

        if (guardRepo.existsByEmail(normalEmail))
            throw new CustomException("Email already in use by another security account", HttpStatus.CONFLICT);

        if (hasPhone && guardRepo.existsByPhone(normalizedPhone))
            throw new CustomException("Phone number already in use by another security account", HttpStatus.CONFLICT);

        SecurityGuard guard = SecurityGuard.builder()
                .name(req.getName().trim())
                .email(normalEmail)
                .phone(hasPhone ? normalizedPhone : null)
                .password(passwordEncoder.encode(req.getPassword()))
                .active(true)
                .createdByAdminId(adminId)
                .build();

        return toResponse(guardRepo.save(guard));
    }

    public List<SecurityGuardDTO.Response> getAll() {
        return guardRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public SecurityGuardDTO.Response getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SecurityGuardDTO.Response update(Long id, SecurityGuardDTO.UpdateRequest req, Long adminId) {
        SecurityGuard guard = findOrThrow(id);

        if (req.getName() != null && !req.getName().isBlank())
            guard.setName(req.getName().trim());

        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            String newPhone = PhoneNormalizer.normalize(req.getPhone());
            if (newPhone != null && !newPhone.equals(guard.getPhone()) && guardRepo.existsByPhone(newPhone))
                throw new CustomException("Phone number already in use", HttpStatus.CONFLICT);
            guard.setPhone(newPhone);
        }

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim().toLowerCase();
            if (!newEmail.equals(guard.getEmail()) && guardRepo.existsByEmail(newEmail))
                throw new CustomException("Email already in use", HttpStatus.CONFLICT);
            guard.setEmail(newEmail);
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            if (req.getPassword().length() < 6)
                throw new CustomException("Password must be at least 6 characters", HttpStatus.BAD_REQUEST);
            guard.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        if (req.getActive() != null)
            guard.setActive(req.getActive());

        return toResponse(guardRepo.save(guard));
    }

    @Transactional
    public void delete(Long id) {
        if (!guardRepo.existsById(id))
            throw new CustomException("Security account not found", HttpStatus.NOT_FOUND);
        guardRepo.deleteById(id);
    }

    // ── Residents list for Security dashboard ─────────────────────────────

    public List<SecurityResidentDTO> getResidentsList() {
        List<Resident> owners = residentRepo.findAll().stream()
                .filter(r -> r.getResidentRole() == Resident.ResidentRole.OWNER)
                .filter(r -> r.getRegistrationStatus() == Resident.RegistrationStatus.APPROVED)
                .filter(Resident::isActive)
                .collect(Collectors.toList());

        return owners.stream().map(owner -> {
            List<FamilyMember> fms =
                    familyMemberRepo.findByResidentIdAndActiveTrueOrderByCreatedAtAsc(owner.getId());

            List<SecurityResidentDTO.FamilyMemberInfo> fmInfos = fms.stream()
                    .map(fm -> SecurityResidentDTO.FamilyMemberInfo.builder()
                            .id(fm.getId())
                            .name(fm.getName())
                            .relationship(fm.getRelationship() != null
                                    ? fm.getRelationship().getDisplayName() : null)
                            .phone(fm.getPhone())
                            .age(fm.getAge())
                            .build())
                    .collect(Collectors.toList());

            return SecurityResidentDTO.builder()
                    .id(owner.getId())
                    .ownerName(owner.getFullName())
                    .flatNumber(owner.getFlatNumber())
                    .phone(owner.getPhone())
                    .flatType(owner.getFlatType())
                    .propertyType(owner.getPropertyType() != null
                            ? owner.getPropertyType().name() : null)
                    .familyMembers(fmInfos)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SecurityGuard findOrThrow(Long id) {
        return guardRepo.findById(id)
                .orElseThrow(() -> new CustomException(
                        "Security account not found", HttpStatus.NOT_FOUND));
    }

    private SecurityGuardDTO.Response toResponse(SecurityGuard g) {
        boolean internal = g.getEmail() != null && g.getEmail().endsWith("@resitrack.internal");
        return SecurityGuardDTO.Response.builder()
                .id(g.getId())
                .name(g.getName())
                .email(internal ? null : g.getEmail())
                .phone(g.getPhone())
                .active(g.isActive())
                .createdByAdminId(g.getCreatedByAdminId())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}