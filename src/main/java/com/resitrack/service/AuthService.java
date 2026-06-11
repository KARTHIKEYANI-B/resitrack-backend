package com.resitrack.service;

import com.resitrack.dto.*;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Resident;
import com.resitrack.entity.Resident.RegistrationStatus;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository     adminRepo;
    private final ResidentRepository  residentRepo;
    private final PasswordEncoder     passwordEncoder;
    private final JwtTokenProvider    jwtTokenProvider;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    // ── Admin Login ───────────────────────────────────────────────────────
    //
    // Supports two login identifiers:
    //   1. Email address   — admin@resitrack.com
    //   2. Mobile number   — 10-digit phone stored on the Admin account
    //
    // The JWT is always issued against admin.getEmail() (unchanged).
    // UserDetailsServiceImpl continues to load by email on every request.
    public JwtResponse adminLogin(LoginRequest req) {
        // Normalise: trim whitespace; treat as email if it contains '@'
        String identifier = req.getEmail() != null ? req.getEmail().trim() : "";

        // Try email first; if not found try phone (mobile-number login)
        Admin admin = adminRepo.findByEmail(identifier)
                .or(() -> adminRepo.findByPhone(identifier))
                .orElseThrow(() -> new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.getPassword(), admin.getPassword()))
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        String token = jwtTokenProvider.generateTokenFromUsername(admin.getEmail());

        // Safely read isSuperAdmin — defaults to false if column not yet migrated
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

    // ── Resident / Family Member Login ────────────────────────────────────
    //
    // Supports two login identifiers:
    //   1. Email address   — owner@gmail.com  /  fm.login@resitrack.internal
    //   2. Mobile number   — 10-digit phone stored on the Resident row
    //
    // Covers: OWNER, FAMILY_MEMBER.
    // The JWT is always issued against r.getEmail() (unchanged).
    // UserDetailsServiceImpl continues to load by email on every request.
    public JwtResponse userLogin(LoginRequest req) {
        // Normalise: trim whitespace
        String identifier = req.getEmail() != null ? req.getEmail().trim() : "";

        // Try email first; if not found try phone (mobile-number login)
        Resident r = residentRepo.findByEmail(identifier)
                .or(() -> residentRepo.findByPhone(identifier))
                .orElseThrow(() -> new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.getPassword(), r.getPassword()))
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);

        // FAMILY_MEMBER accounts skip PENDING/REJECTED checks (auto-approved at creation)
        boolean isFamilyMember = false;
        try {
            isFamilyMember = r.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER;
        } catch (Exception ignored) {}

        if (!isFamilyMember) {
            if (r.getRegistrationStatus() == RegistrationStatus.PENDING) {
                String date = r.getCreatedAt() != null
                        ? r.getCreatedAt().format(DATE_FMT) : "recently";
                throw new CustomException(
                        "PENDING:Your registration is pending admin approval. Submitted on " + date + ".",
                        HttpStatus.FORBIDDEN);
            }
            if (r.getRegistrationStatus() == RegistrationStatus.REJECTED) {
                String reason = r.getRejectedReason() != null
                        ? r.getRejectedReason() : "No reason provided";
                throw new CustomException("REJECTED:" + reason, HttpStatus.FORBIDDEN);
            }
        }

        if (!r.isActive()) {
            throw new CustomException(
                    "INACTIVE:Your account has been deactivated. Please contact the admin.",
                    HttpStatus.FORBIDDEN);
        }

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

        // Safe residentRole read (field may not exist if entity not updated)
        try {
            String role = r.getResidentRole().name();
            builder.residentRole(role);
            if (r.getResidentRole() == Resident.ResidentRole.FAMILY_MEMBER) {
                builder.ownerResidentId(r.getOwnerResidentId());
                builder.familyMemberId(r.getFamilyMemberId());
            }
        } catch (Exception ignored) {
            builder.residentRole("OWNER");
        }

        return JwtResponse.builder().token(token).user(builder.build()).build();
    }

    // ── Self Registration ─────────────────────────────────────────────────
    @Transactional
    public Resident register(RegisterRequest req) {

        if (residentRepo.existsByEmail(req.getEmail()))
            throw new CustomException("Email is already registered.", HttpStatus.CONFLICT);

        if (req.getPhone() != null && !req.getPhone().isBlank()
                && residentRepo.existsByPhone(req.getPhone()))
            throw new CustomException("Phone number is already registered.", HttpStatus.CONFLICT);

        if (req.getFlatNumber() != null && !req.getFlatNumber().isBlank()
                && residentRepo.existsByFlatNumber(req.getFlatNumber()))
            throw new CustomException("Flat/Villa number is already registered.", HttpStatus.CONFLICT);

        Resident.ResidentBuilder builder = Resident.builder()
                .fullName(req.getFullName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
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

        // Safe: set residentRole only if the field exists on the entity
        try {
            builder.residentRole(Resident.ResidentRole.OWNER);
        } catch (Exception ignored) {}

        Resident saved = residentRepo.save(builder.build());
        notifyAllAdmins(saved);
        return saved;
    }

    // ── Registration status check ─────────────────────────────────────────
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

    // ── Register number validation ─────────────────────────────────────────
    public void validateRegisterNumber(String regNo) {
        Resident r = residentRepo.findByRegisterNumber(regNo)
                .orElseThrow(() -> new CustomException(
                        "Invalid register number. Contact your admin.", HttpStatus.NOT_FOUND));
        if (r.isRegistered())
            throw new CustomException("Register number already used.", HttpStatus.CONFLICT);
    }

    // ── Admin change password ─────────────────────────────────────────────
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

    // ── Resident change password ──────────────────────────────────────────
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

    // ── Private helpers ───────────────────────────────────────────────────
    private void notifyAllAdmins(Resident r) {
        List<Admin> admins = adminRepo.findAll();
        for (Admin admin : admins) {
            notificationService.notifyAdminNewRegistration(admin.getId(), r);
        }
    }
}