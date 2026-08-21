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
    private final RefreshTokenService          refreshTokenService;
    private final NotificationService          notificationService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    public JwtResponse unifiedLogin(LoginRequest req) {
        String identifier     = req.getEmail() != null ? req.getEmail().trim() : "";
        String normalizedPhone = PhoneNormalizer.normalize(identifier);
        String password        = req.getPassword();

        // ── Cross-role identifier collisions ────────────────────────────────
        // A phone number is only guaranteed unique *within* the residents
        // table. An Admin account's phone is a denormalized copy synced from
        // whichever resident currently holds that committee position (see
        // AdminAssignmentService.appoint / MemberService.updateMember), so the
        // SAME phone number can legitimately exist on both an Admin row and a
        // Resident row at once — e.g. an Owner who is also the Secretary.
        //
        // Previously, the first role whose findByEmail/findByPhone matched
        // "won" outright: if that role's password check failed we threw
        // "Invalid credentials" immediately, even though a *different* role
        // sharing the same identifier and the password the person actually
        // typed existed further down the chain (typically the Owner account,
        // checked after Admin). That meant an Owner+Admin with a shared phone
        // could never log in with their Owner password.
        //
        // Fix: identifier matching a role no longer short-circuits the whole
        // login. Only a *correct password* for that role's account counts as
        // a match; otherwise we fall through and keep checking the remaining
        // roles in the same priority order as before. "Invalid credentials"
        // is now only thrown once none of the matching accounts accept the
        // supplied password. Role-specific post-auth checks (security guard
        // active flag, resident approval/active status, etc.) are unchanged
        // and still only run once a password has actually matched.

        // 1. Admin check (email or phone)
        Admin admin = adminRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(adminRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (admin != null && passwordEncoder.matches(password, admin.getPassword())) {
            return buildAdminResponse(admin, req.getDeviceType());
        }

        // 2. Security guard check (email or phone)
        SecurityGuard guard = securityGuardRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(securityGuardRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (guard != null && passwordEncoder.matches(password, guard.getPassword())) {
            if (!guard.isActive())
                throw new CustomException(
                        "INACTIVE:Your security account has been deactivated. Contact the admin.",
                        HttpStatus.FORBIDDEN);
            return buildSecurityResponse(guard, req.getDeviceType());
        }

        // 3. Resident / Owner check (email or phone — login credentials)
        Resident r = residentRepo.findByEmail(identifier)
                .or(() -> findByNormalizedPhone(residentRepo::findByPhone, normalizedPhone))
                .orElse(null);
        if (r != null && passwordEncoder.matches(password, r.getPassword())) {
            return buildResidentResponse(r, req.getDeviceType());
        }

        // 4. Family Member check (personal email or phone on the family_members row)
        Resident fmLoginAccount = resolveFamilyMemberLoginByPersonalContact(identifier);
        if (fmLoginAccount != null && passwordEncoder.matches(password, fmLoginAccount.getPassword())) {
            return buildResidentResponse(fmLoginAccount, req.getDeviceType());
        }

        // Nothing matched an identifier+password pair across any role
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

        return buildAdminResponse(admin, req.getDeviceType());
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

        return buildSecurityResponse(guard, req.getDeviceType());
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

        return buildResidentResponse(r, req.getDeviceType());
    }

    // ── Remember Me / Auto-Login: Refresh & Logout ─────────────────────────

    /**
     * Exchanges a valid, unexpired, non-revoked refresh token for a brand
     * new access token. Does NOT rotate/replace the refresh token itself
     * (matches the documented /auth/refresh response contract, which only
     * returns {accessToken, expiresIn}) — the same refresh token keeps
     * working for further refreshes until it expires (30 days) or is
     * revoked (logout, or an admin/security action elsewhere).
     */
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        var validated = refreshTokenService.validate(refreshToken);
        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(validated.getUsername());
        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .build();
    }

    /**
     * Revokes the given refresh token so it (and therefore the ability to
     * silently mint new access tokens from it) can never be used again.
     * Always succeeds from the caller's point of view — an already-revoked,
     * expired, or unknown token is treated as "already logged out" rather
     * than an error, so the client can safely call this during its own
     * logout flow without special-casing failures.
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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

    private JwtResponse buildAdminResponse(Admin admin, String deviceType) {
        // Mirrors the Resident/SecurityGuard inactive-account check — lets a
        // System Owner deactivate a Super Admin (or any admin) account
        // without deleting it. Defensive try/catch matches the pattern
        // below (isSuperAdmin) for the same reason: don't hard-fail login
        // for every admin if this column is ever momentarily unavailable.
        boolean activeFlag = true;
        try { activeFlag = admin.isActive(); } catch (Exception ignored) {}
        if (!activeFlag)
            throw new CustomException(
                    "INACTIVE:Your admin account has been deactivated. Contact the System Owner.",
                    HttpStatus.FORBIDDEN);

        String accessToken  = jwtTokenProvider.generateTokenFromUsername(admin.getEmail());
        String refreshToken = refreshTokenService.issue("ADMIN", admin.getId(), admin.getEmail(), deviceType);
        boolean superAdminFlag = false;
        try { superAdminFlag = admin.isSuperAdmin(); } catch (Exception ignored) {}
        boolean systemOwnerFlag = false;
        try { systemOwnerFlag = admin.isSystemOwner(); } catch (Exception ignored) {}
        boolean viewerFlag = false;
        try { viewerFlag = admin.isViewer(); } catch (Exception ignored) {}
        return JwtResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .user(JwtResponse.UserInfo.builder()
                        .id(admin.getId())
                        .name(admin.getName())
                        .email(admin.getEmail())
                        .role("ADMIN")
                        .superAdmin(superAdminFlag)
                        .systemOwner(systemOwnerFlag)
                        .viewer(viewerFlag)
                        .build())
                .build();
    }

    private JwtResponse buildSecurityResponse(SecurityGuard guard, String deviceType) {
        String accessToken  = jwtTokenProvider.generateTokenFromUsername(guard.getEmail());
        String refreshToken = refreshTokenService.issue("SECURITY", guard.getId(), guard.getEmail(), deviceType);
        return JwtResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .user(JwtResponse.UserInfo.builder()
                        .id(guard.getId())
                        .name(guard.getName())
                        .email(guard.getEmail())
                        .role("SECURITY")
                        .build())
                .build();
    }

    private JwtResponse buildResidentResponse(Resident r, String deviceType) {
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

        String accessToken  = jwtTokenProvider.generateTokenFromUsername(r.getEmail());
        String refreshToken = refreshTokenService.issue("USER", r.getId(), r.getEmail(), deviceType);

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

        return JwtResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .user(builder.build())
                .build();
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