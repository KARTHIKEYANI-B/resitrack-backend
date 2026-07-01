package com.resitrack.service;

import com.resitrack.dto.*;
import com.resitrack.entity.Admin;
import com.resitrack.entity.FamilyMember;
import com.resitrack.entity.Resident;
import com.resitrack.entity.Resident.RegistrationStatus;
import com.resitrack.entity.SecurityGuard;
import com.resitrack.entity.Vehicle;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.SecurityGuardRepository;
import com.resitrack.repository.VehicleRepository;
import com.resitrack.security.JwtTokenProvider;
import com.resitrack.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository              adminRepo;
    private final ResidentRepository           residentRepo;
    private final SecurityGuardRepository      securityGuardRepo;   
    private final FamilyMemberRepository       familyMemberRepo;    
    private final VehicleRepository            vehicleRepo;
    private final VehicleDocumentUploadService vehicleDocumentUploadService;
    private final PasswordEncoder              passwordEncoder;
    private final JwtTokenProvider             jwtTokenProvider;
    private final NotificationService          notificationService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    public JwtResponse unifiedLogin(LoginRequest req) {
        String identifier     = req.getEmail() != null ? req.getEmail().trim() : "";
        String normalizedPhone = PhoneNormalizer.normalize(identifier);
        String password        = req.getPassword();

        // 1. Admin check (email or phone)
        Admin admin = adminRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(adminRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (admin != null) {
            if (!passwordEncoder.matches(password, admin.getPassword()))
                throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
            return buildAdminResponse(admin);
        }

        // 2. Security guard check (email or phone)
        SecurityGuard guard = securityGuardRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(securityGuardRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (guard != null) {
            if (!passwordEncoder.matches(password, guard.getPassword()))
                throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
            if (!guard.isActive())
                throw new CustomException(
                        "INACTIVE:Your security account has been deactivated. Contact the admin.",
                        HttpStatus.FORBIDDEN);
            return buildSecurityResponse(guard);
        }

        // 3. Resident / Family Member check (email or phone — login credentials)
        Resident r = residentRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(residentRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (r != null) {
            if (!passwordEncoder.matches(password, r.getPassword()))
                throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
            return buildResidentResponse(r);
        }

        Resident fmLoginAccount = resolveFamilyMemberLoginByPersonalContact(identifier);
        if (fmLoginAccount != null) {
            if (!passwordEncoder.matches(password, fmLoginAccount.getPassword()))
                throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
            return buildResidentResponse(fmLoginAccount);
        }

        // Nothing matched
        throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
    }

    // ── Admin Login (unchanged — kept for backward compat) ────────────────
    public JwtResponse adminLogin(LoginRequest req) {
        String identifier      = req.getEmail() != null ? req.getEmail().trim() : "";
        String normalizedPhone = PhoneNormalizer.normalize(identifier);

        Admin admin = adminRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(adminRepo::findByPhone, normalizedPhone))
                .orElseThrow(() -> new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.getPassword(), admin.getPassword()))
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        return buildAdminResponse(admin);
    }

    // ── Security Guard Login (added alongside security module) ────────────
    public JwtResponse securityLogin(LoginRequest req) {
        String identifier      = req.getEmail() != null ? req.getEmail().trim() : "";
        String normalizedPhone = PhoneNormalizer.normalize(identifier);

        SecurityGuard guard = securityGuardRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(securityGuardRepo::findByPhone, normalizedPhone))
                .orElseThrow(() -> new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.getPassword(), guard.getPassword()))
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        if (!guard.isActive())
            throw new CustomException(
                    "INACTIVE:Your security account has been deactivated. Contact the admin.",
                    HttpStatus.FORBIDDEN);

        return buildSecurityResponse(guard);
    }

    // ── Resident / Family Member Login (unchanged — kept for backward compat)
    public JwtResponse userLogin(LoginRequest req) {
        String identifier      = req.getEmail() != null ? req.getEmail().trim() : "";
        String normalizedPhone = PhoneNormalizer.normalize(identifier);

        Resident r = residentRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(residentRepo::findByPhone, normalizedPhone))
                .orElse(null);

        if (r == null) {
            // Fallback: check Family Member personal contact email/phone
            r = resolveFamilyMemberLoginByPersonalContact(identifier);
        }

        if (r == null)
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        if (!passwordEncoder.matches(req.getPassword(), r.getPassword()))
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        return buildResidentResponse(r);
    }

    // ── Self Registration (unchanged behavior; now also creates a Vehicle row) ──
    @Transactional
    public Resident register(RegisterRequest req) {

        if (residentRepo.existsByEmail(req.getEmail()))
            throw new CustomException("Email is already registered.", HttpStatus.CONFLICT);

        String normalizedPhone = PhoneNormalizer.normalize(req.getPhone());

        if (normalizedPhone != null && residentRepo.existsByPhone(normalizedPhone))
            throw new CustomException("Phone number is already registered.", HttpStatus.CONFLICT);

        if (req.getFlatNumber() != null && !req.getFlatNumber().isBlank()
                && residentRepo.existsByFlatNumber(req.getFlatNumber()))
            throw new CustomException("Flat/Villa number is already registered.", HttpStatus.CONFLICT);

        Resident.ResidentBuilder builder = Resident.builder()
                .fullName(req.getFullName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(req.getPassword()))
                .flatNumber(req.getFlatNumber() != null
                        ? req.getFlatNumber().trim().toUpperCase() : null)
                .flatType(req.getFlatType())
                .propertyType(req.getPropertyType())
                .sqFt(req.getSqFt())
                .familyMembers(req.getFamilyMembers())
                .age(req.getAge())
                .vehicleDetails(req.getVehicleDetails())
                .address(req.getAddress())
                .registrationStatus(RegistrationStatus.PENDING)
                .isApproved(false)
                .isActive(false)
                .registered(false)
                .status(Resident.ResidentStatus.INACTIVE);

        try { builder.residentRole(Resident.ResidentRole.OWNER); } catch (Exception ignored) {}

        Resident saved = residentRepo.save(builder.build());

        // ── Multiple Vehicles support ───────────────────────────────────────
        // The legacy single "vehicleDetails" string on Resident is left exactly
        // as-is for backward compatibility. In addition, if a vehicle number
        // was supplied at registration, also create the first Vehicle row so
        // it immediately shows up in "Owner Account → Settings → Vehicle /
        // Insurance" once the owner is approved and logs in.
        createVehicleIfPresent(saved, req.getVehicleDetails(), null);

        notifyAllAdmins(saved);
        return saved;
    }

    /**
     * Same as {@link #register(RegisterRequest)} but additionally accepts an
     * optional insurance document (image or PDF) for the vehicle supplied in
     * the request. Used by the multipart registration endpoint so an owner
     * can upload their insurance document for the vehicle in the same step,
     * without requiring a separate authenticated call (the resident is not
     * yet approved/active and therefore cannot log in to call /user/vehicles
     * right after registering).
     */
    @Transactional
    public Resident registerWithVehicleDocument(RegisterRequest req, MultipartFile insuranceDocument) {
        if (insuranceDocument != null && !insuranceDocument.isEmpty()
                && (req.getVehicleDetails() == null || req.getVehicleDetails().isBlank())) {
            throw new CustomException(
                    "Vehicle Number is required to attach an insurance document.",
                    HttpStatus.BAD_REQUEST);
        }

        if (residentRepo.existsByEmail(req.getEmail()))
            throw new CustomException("Email is already registered.", HttpStatus.CONFLICT);

        String normalizedPhone = PhoneNormalizer.normalize(req.getPhone());

        if (normalizedPhone != null && residentRepo.existsByPhone(normalizedPhone))
            throw new CustomException("Phone number is already registered.", HttpStatus.CONFLICT);

        if (req.getFlatNumber() != null && !req.getFlatNumber().isBlank()
                && residentRepo.existsByFlatNumber(req.getFlatNumber()))
            throw new CustomException("Flat/Villa number is already registered.", HttpStatus.CONFLICT);

        Resident.ResidentBuilder builder = Resident.builder()
                .fullName(req.getFullName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(req.getPassword()))
                .flatNumber(req.getFlatNumber() != null
                        ? req.getFlatNumber().trim().toUpperCase() : null)
                .flatType(req.getFlatType())
                .propertyType(req.getPropertyType())
                .sqFt(req.getSqFt())
                .familyMembers(req.getFamilyMembers())
                .age(req.getAge())
                .vehicleDetails(req.getVehicleDetails())
                .address(req.getAddress())
                .registrationStatus(RegistrationStatus.PENDING)
                .isApproved(false)
                .isActive(false)
                .registered(false)
                .status(Resident.ResidentStatus.INACTIVE);

        try { builder.residentRole(Resident.ResidentRole.OWNER); } catch (Exception ignored) {}

        Resident saved = residentRepo.save(builder.build());

        createVehicleIfPresent(saved, req.getVehicleDetails(), insuranceDocument);

        notifyAllAdmins(saved);
        return saved;
    }

    private void createVehicleIfPresent(Resident owner, String vehicleNumber, MultipartFile insuranceDocument) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) return;

        Vehicle.VehicleBuilder vBuilder = Vehicle.builder()
                .resident(owner)
                .vehicleNumber(vehicleNumber.trim().toUpperCase())
                .active(true);

        Vehicle vehicle = vehicleRepo.save(vBuilder.build());

        if (insuranceDocument != null && !insuranceDocument.isEmpty()) {
            String relativePath = vehicleDocumentUploadService.saveInsuranceDocument(insuranceDocument);
            vehicle.setInsuranceDocumentPath(relativePath);
            vehicle.setInsuranceDocumentName(insuranceDocument.getOriginalFilename());
            vehicleRepo.save(vehicle);
        }
    }

    // ── Registration status check (unchanged) ─────────────────────────────
    public RegistrationStatusDTO getRegistrationStatus(String email) {
        Resident r = residentRepo.findByEmail(email)
                .orElseThrow(() -> new CustomException(
                        "No registration found for this email.", HttpStatus.NOT_FOUND));

        return RegistrationStatusDTO.builder()
                .name(r.getFullName())
                .email(r.getEmail())
                .status(r.getRegistrationStatus().name())
                .registrationDate(r.getCreatedAt() != null
                        ? r.getCreatedAt().format(DATE_FMT) : "—")
                .rejectedReason(r.getRejectedReason())
                .approvedAt(r.getApprovedAt() != null
                        ? r.getApprovedAt().format(DATE_FMT) : null)
                .flatNumber(r.getFlatNumber())
                .flatType(r.getFlatType())
                .build();
    }

    // ── Register number validation (unchanged) ─────────────────────────────
    public void validateRegisterNumber(String regNo) {
        Resident r = residentRepo.findByRegisterNumber(regNo)
                .orElseThrow(() -> new CustomException(
                        "Invalid register number. Contact your admin.", HttpStatus.NOT_FOUND));
        if (r.isRegistered())
            throw new CustomException("Register number already used.", HttpStatus.CONFLICT);
    }

    // ── Admin change password (unchanged) ─────────────────────────────────
    public void changeAdminPassword(String adminEmail, ChangePasswordRequest req) {
        Admin admin = adminRepo.findByEmail(adminEmail)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(req.getCurrentPassword(), admin.getPassword()))
            throw new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        if (req.getNewPassword() == null || req.getNewPassword().trim().length() < 8)
            throw new CustomException("New password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        admin.setPassword(passwordEncoder.encode(req.getNewPassword()));
        adminRepo.save(admin);
    }

    // ── Resident change password (unchanged) ──────────────────────────────
    public void changeResidentPassword(Long residentId, ChangePasswordRequest req) {
        Resident r = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(req.getCurrentPassword(), r.getPassword()))
            throw new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        if (req.getNewPassword() == null || req.getNewPassword().trim().length() < 8)
            throw new CustomException("New password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        r.setPassword(passwordEncoder.encode(req.getNewPassword()));
        residentRepo.save(r);
    }

    // ── Security change password (security module) ────────────────────────
    public void changeSecurityPassword(String guardEmail, ChangePasswordRequest req) {
        SecurityGuard guard = securityGuardRepo.findByEmail(guardEmail)
                .orElseThrow(() -> new CustomException(
                        "Security account not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(req.getCurrentPassword(), guard.getPassword()))
            throw new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        if (req.getNewPassword() == null || req.getNewPassword().trim().length() < 6)
            throw new CustomException("New password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        guard.setPassword(passwordEncoder.encode(req.getNewPassword()));
        securityGuardRepo.save(guard);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private JwtResponse buildAdminResponse(Admin admin) {
        String token = jwtTokenProvider.generateTokenFromUsername(admin.getEmail());
        boolean superAdminFlag = false;
        try { superAdminFlag = admin.isSuperAdmin(); } catch (Exception ignored) {}
        return JwtResponse.builder()
                .token(token)
                .user(JwtResponse.UserInfo.builder()
                        .id(admin.getId())
                        .name(admin.getName())
                        .email(admin.getEmail())
                        .role("ADMIN")
                        .superAdmin(superAdminFlag)
                        .build())
                .build();
    }

    private JwtResponse buildSecurityResponse(SecurityGuard guard) {
        String token = jwtTokenProvider.generateTokenFromUsername(guard.getEmail());
        return JwtResponse.builder()
                .token(token)
                .user(JwtResponse.UserInfo.builder()
                        .id(guard.getId())
                        .name(guard.getName())
                        .email(guard.getEmail())
                        .role("SECURITY")
                        .build())
                .build();
    }

    private JwtResponse buildResidentResponse(Resident r) {
        boolean isFamilyMember = false;
        try { isFamilyMember = r.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER; }
        catch (Exception ignored) {}

        if (!isFamilyMember) {
            if (r.getRegistrationStatus() == RegistrationStatus.PENDING) {
                String date = r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : "recently";
                throw new CustomException(
                        "PENDING:Your registration is pending admin approval. Submitted on " + date + ".",
                        HttpStatus.FORBIDDEN);
            }
            if (r.getRegistrationStatus() == RegistrationStatus.REJECTED) {
                String reason = r.getRejectedReason() != null ? r.getRejectedReason() : "No reason provided";
                throw new CustomException("REJECTED:" + reason, HttpStatus.FORBIDDEN);
            }
        }

        if (!r.isActive())
            throw new CustomException(
                    "INACTIVE:Your account has been deactivated. Please contact the admin.",
                    HttpStatus.FORBIDDEN);

        String token = jwtTokenProvider.generateTokenFromUsername(r.getEmail());

        JwtResponse.UserInfo.UserInfoBuilder builder = JwtResponse.UserInfo.builder()
                .id(r.getId())
                .name(r.getFullName())
                .email(r.getEmail())
                .role("USER")
                .flatNumber(r.getFlatNumber())
                .flatType(r.getFlatType())
                .propertyType(r.getPropertyType() != null ? r.getPropertyType().name() : null)
                .registerNumber(r.getRegisterNumber())
                .registrationStatus(r.getRegistrationStatus() != null
                        ? r.getRegistrationStatus().name() : null);

        try {
            builder.residentRole(r.getResidentRole().name());
            if (r.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER) {
                builder.ownerResidentId(r.getOwnerResidentId());
                builder.familyMemberId(r.getFamilyMemberId());
            }
        } catch (Exception ignored) {
            builder.residentRole("OWNER");
        }

        return JwtResponse.builder().token(token).user(builder.build()).build();
    }

    private void notifyAllAdmins(Resident r) {
        List<Admin> admins = adminRepo.findAll();
        for (Admin admin : admins) {
            notificationService.notifyAdminNewRegistration(admin.getId(), r);
        }
    }


    private Resident resolveFamilyMemberLoginByPersonalContact(String identifier) {
        FamilyMember fm = familyMemberRepo.findByEmailAndHasAppAccessTrue(identifier).orElse(null);

        if (fm == null) {
            String normalizedPhone = PhoneNormalizer.normalize(identifier);
            if (normalizedPhone != null) {
                fm = familyMemberRepo.findByPhoneAndHasAppAccessTrue(normalizedPhone).orElse(null);
            }
        }

        if (fm == null || fm.getUserId() == null) return null;

        return residentRepo.findById(fm.getUserId()).orElse(null);
    }

    /**
     * Runs a repository's findByPhone(...) lookup only when the identifier
     * actually normalized to something phone-shaped. Centralizes the
     * null-guard so every login path treats "identifier wasn't a phone
     * number at all" the same way (skip the lookup) instead of querying
     * with a null/blank value.
     */
    private <T> Optional<T> findByNormalizedPhone(
            Function<String, Optional<T>> findByPhone, String normalizedPhone) {
        return normalizedPhone != null ? findByPhone.apply(normalizedPhone) : Optional.empty();
    }
}