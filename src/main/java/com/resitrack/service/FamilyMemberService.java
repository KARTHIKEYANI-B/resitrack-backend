package com.resitrack.service;

import com.resitrack.dto.FamilyMemberDTO;
import com.resitrack.entity.FamilyMember;
import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyMemberService {

    private final FamilyMemberRepository familyMemberRepo;
    private final ResidentRepository     residentRepo;
    private final PasswordEncoder        passwordEncoder;

    // ── Get all family members for the logged-in owner ────────────────────
    public List<FamilyMemberDTO.Response> getMyFamilyMembers(Long ownerId) {
        return familyMemberRepo.findByResidentIdAndActiveTrueOrderByCreatedAtAsc(ownerId)
                .stream()
                .map(fm -> {
                    String loginEmail = null;
                    if (fm.getUserId() != null) {
                        loginEmail = residentRepo.findById(fm.getUserId())
                                .map(Resident::getEmail)
                                .orElse(null);
                    }
                    return FamilyMemberDTO.Response.from(fm, loginEmail);
                })
                .toList();
    }

    // ── Get single family member (must belong to owner) ────────────────────
    public FamilyMemberDTO.Response getById(Long memberId, Long ownerId) {
        FamilyMember fm = findAndVerifyOwner(memberId, ownerId);
        String loginEmail = resolveLoginEmail(fm);
        return FamilyMemberDTO.Response.from(fm, loginEmail);
    }

    // ── Add family member ──────────────────────────────────────────────────
    @Transactional
    public FamilyMemberDTO.Response addFamilyMember(Long ownerId, FamilyMemberDTO.Request req) {
        validate(req);
        Resident owner = residentRepo.findById(ownerId)
                .orElseThrow(() -> new CustomException("Owner not found", HttpStatus.NOT_FOUND));

        FamilyMember.Relationship relationship = parseRelationship(req.getRelationship());

        FamilyMember fm = FamilyMember.builder()
                .resident(owner)
                .name(req.getName().trim())
                .relationship(relationship)
                .age(req.getAge())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .email(req.getEmail() != null ? req.getEmail().trim().toLowerCase() : null)
                .hasAppAccess(false)
                .active(true)
                .build();

        FamilyMember saved = familyMemberRepo.save(fm);
        log.info("Family member added: {} for owner {}", saved.getName(), ownerId);
        return FamilyMemberDTO.Response.from(saved);
    }

    // ── Update family member ───────────────────────────────────────────────
    @Transactional
    public FamilyMemberDTO.Response updateFamilyMember(Long memberId, Long ownerId,
                                                        FamilyMemberDTO.Request req) {
        FamilyMember fm = findAndVerifyOwner(memberId, ownerId);
        validate(req);

        if (req.getName()         != null) fm.setName(req.getName().trim());
        if (req.getRelationship() != null) fm.setRelationship(parseRelationship(req.getRelationship()));
        if (req.getAge()          != null) fm.setAge(req.getAge());

        // Track whether phone is changing so we can sync the login Resident row
        String oldPhone = fm.getPhone();
        String newPhone = null;
        if (req.getPhone() != null) {
            newPhone = req.getPhone().trim();
            fm.setPhone(newPhone);
        }

        if (req.getEmail()        != null) fm.setEmail(req.getEmail().trim().toLowerCase());

        FamilyMember saved = familyMemberRepo.save(fm);

        final String phoneToSync = newPhone;
        if (saved.getUserId() != null && phoneToSync != null
                && !phoneToSync.equals(oldPhone)) {
            residentRepo.findById(saved.getUserId()).ifPresent(loginResident -> {
                if (loginResident.isActive()) {
                    String currentResPhone = loginResident.getPhone();
                    boolean phoneIsAvailable = !residentRepo.existsByPhone(phoneToSync)
                            || phoneToSync.equals(currentResPhone); // same row, no collision
                    if (phoneIsAvailable) {
                        loginResident.setPhone(phoneToSync);
                        residentRepo.save(loginResident);
                        log.info("Synced phone {} to login Resident row for FM {}",
                                phoneToSync, saved.getId());
                    } else {
                        log.warn("Phone {} already taken — login Resident for FM {} not updated",
                                phoneToSync, saved.getId());
                    }
                }
            });
        }

        log.info("Family member updated: {}", saved.getId());
        return FamilyMemberDTO.Response.from(saved, resolveLoginEmail(saved));
    }

    @Transactional
    public void removeFamilyMember(Long memberId, Long ownerId) {
        FamilyMember fm = findAndVerifyOwner(memberId, ownerId);

        if (fm.isHasAppAccess()) {
            revokeAppAccess(memberId, ownerId);
            fm = familyMemberRepo.findById(memberId).orElseThrow(); // reload after revoke
        }

        fm.setActive(false);
        familyMemberRepo.save(fm);
        log.info("Family member soft-deleted: {}", memberId);
    }

    @Transactional
    public FamilyMemberDTO.Response grantAppAccess(Long memberId, Long ownerId,
                                                    FamilyMemberDTO.GrantAccessRequest req) {
        FamilyMember fm = findAndVerifyOwner(memberId, ownerId);

        if (fm.isHasAppAccess()) {
            throw new CustomException("App access already granted to this family member",
                    HttpStatus.CONFLICT);
        }

        String loginEmail = req.getLoginEmail();
        if (loginEmail == null || loginEmail.isBlank()) {
            throw new CustomException("Login email is required", HttpStatus.BAD_REQUEST);
        }
        loginEmail = loginEmail.trim().toLowerCase();

        if (residentRepo.existsByEmail(loginEmail)) {
            throw new CustomException("This email is already registered in the system",
                    HttpStatus.CONFLICT);
        }

        String password = req.getPassword();
        if (password == null || password.trim().length() < 8) {
            throw new CustomException("Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        }

        Resident owner = residentRepo.findById(ownerId)
                .orElseThrow(() -> new CustomException("Owner not found", HttpStatus.NOT_FOUND));

        String regNum = "FM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String loginPhone = null;
        if (fm.getPhone() != null && !fm.getPhone().isBlank()) {
            String trimmedPhone = fm.getPhone().trim();
            if (!residentRepo.existsByPhone(trimmedPhone)) {
                loginPhone = trimmedPhone;
            } else {
                log.warn("Phone {} already registered to another account — FM {} will use email login only",
                        trimmedPhone, memberId);
            }
        }

        Resident loginAccount = Resident.builder()
                .fullName(fm.getName())
                .email(loginEmail)
                .phone(loginPhone)                     // enables mobile-number login
                .password(passwordEncoder.encode(password))
                .flatNumber(owner.getFlatNumber())  // same flat as owner
                .flatType(owner.getFlatType())
                .propertyType(owner.getPropertyType() != null
                        ? owner.getPropertyType() : PropertyType.FLAT)
                .registerNumber(regNum)
                .isApproved(true)
                .isActive(true)
                .registered(true)
                .registrationStatus(Resident.RegistrationStatus.APPROVED)
                .status(Resident.ResidentStatus.ACTIVE)
                .residentRole(Resident.ResidentRole.FAMILY_MEMBER)
                .ownerResidentId(ownerId)
                .familyMemberId(memberId)
                .build();

        Resident savedAccount = residentRepo.save(loginAccount);

        fm.setUserId(savedAccount.getId());
        fm.setHasAppAccess(true);
        familyMemberRepo.save(fm);

        log.info("App access granted to family member {} (login: {})", memberId, loginEmail);
        return FamilyMemberDTO.Response.from(fm, loginEmail);
    }

    @Transactional
    public FamilyMemberDTO.Response revokeAppAccess(Long memberId, Long ownerId) {
        FamilyMember fm = findAndVerifyOwner(memberId, ownerId);

        if (!fm.isHasAppAccess()) {
            throw new CustomException("This family member does not have app access",
                    HttpStatus.BAD_REQUEST);
        }

        if (fm.getUserId() != null) {
            residentRepo.findById(fm.getUserId()).ifPresent(loginAccount -> {
                loginAccount.setActive(false);
                loginAccount.setStatus(Resident.ResidentStatus.INACTIVE);
                residentRepo.save(loginAccount);
            });
        }

        fm.setHasAppAccess(false);
        familyMemberRepo.save(fm);

        log.info("App access revoked for family member {}", memberId);
        return FamilyMemberDTO.Response.from(fm, null);
    }

    private FamilyMember findAndVerifyOwner(Long memberId, Long ownerId) {
        FamilyMember fm = familyMemberRepo.findById(memberId)
                .orElseThrow(() -> new CustomException("Family member not found", HttpStatus.NOT_FOUND));
        if (!fm.getResident().getId().equals(ownerId)) {
            throw new CustomException("Access denied: this family member does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!fm.isActive()) {
            throw new CustomException("Family member has been removed", HttpStatus.NOT_FOUND);
        }
        return fm;
    }

    private String resolveLoginEmail(FamilyMember fm) {
        if (fm.getUserId() == null) return null;
        return residentRepo.findById(fm.getUserId())
                .map(Resident::getEmail)
                .orElse(null);
    }

    private FamilyMember.Relationship parseRelationship(String rel) {
        try {
            return FamilyMember.Relationship.valueOf(rel.toUpperCase());
        } catch (Exception e) {
            throw new CustomException("Invalid relationship: " + rel, HttpStatus.BAD_REQUEST);
        }
    }

    private void validate(FamilyMemberDTO.Request req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new CustomException("Name is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getRelationship() == null || req.getRelationship().isBlank()) {
            throw new CustomException("Relationship is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getAge() != null && (req.getAge() < 0 || req.getAge() > 120)) {
            throw new CustomException("Age must be between 0 and 120", HttpStatus.BAD_REQUEST);
        }
    }
}