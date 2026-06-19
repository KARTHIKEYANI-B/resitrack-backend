package com.resitrack.service;

import com.resitrack.dto.AdminAssignmentDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.AdminAssignment;
import com.resitrack.entity.Member;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminAssignmentRepository;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.MemberRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAssignmentService {

    private final AdminRepository           adminRepo;
    private final AdminAssignmentRepository assignmentRepo;
    private final ResidentRepository        residentRepo;
    private final MemberRepository          memberRepo;
    private final PasswordEncoder           passwordEncoder;

    private static final Map<Member.Position, String> POSITION_EMAILS = Map.of(
        Member.Position.PRESIDENT,       "superadmin@gmail.com",
        Member.Position.VICE_PRESIDENT,  "vicepresident@gmail.com",
        Member.Position.SECRETARY,       "secretary@gmail.com",
        Member.Position.JOINT_SECRETARY, "joinsecratery@gmail.com",
        Member.Position.TREASURER,       "treasurer@gmail.com"
    );

    private static final Map<Member.Position, String> POSITION_DISPLAY = Map.of(
        Member.Position.PRESIDENT,       "President",
        Member.Position.VICE_PRESIDENT,  "Vice President",
        Member.Position.SECRETARY,       "Secretary",
        Member.Position.JOINT_SECRETARY, "Joint Secretary",
        Member.Position.TREASURER,       "Treasurer"
    );

    public List<AdminAssignmentDTO.Response> getActiveAssignments() {
        return assignmentRepo.findByActiveTrueOrderByPositionAsc()
                .stream()
                .map(AdminAssignmentDTO.Response::from)
                .collect(Collectors.toList());
    }

    public List<AdminAssignmentDTO.Response> getAssignmentHistory(Long residentId) {
        return assignmentRepo.findByResidentIdOrderByStartDateDesc(residentId)
                .stream()
                .map(AdminAssignmentDTO.Response::from)
                .collect(Collectors.toList());
    }

    public List<AdminAssignmentDTO.Response> getPositionHistory(String positionStr) {
        Member.Position position = parsePosition(positionStr);
        return assignmentRepo.findByPositionOrderByStartDateDesc(position)
                .stream()
                .map(AdminAssignmentDTO.Response::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminAssignmentDTO.AppointResponse appoint(AdminAssignmentDTO.AppointRequest req) {
        Resident resident = residentRepo.findById(req.getResidentId())
                .orElseThrow(() -> new CustomException(
                        "Resident not found: " + req.getResidentId(), HttpStatus.NOT_FOUND));

        Member.Position position = parsePosition(req.getPosition());

        String positionEmail = POSITION_EMAILS.get(position);
        if (resident.getEmail() != null &&
                resident.getEmail().equalsIgnoreCase(positionEmail)) {
            throw new CustomException(
                    "A resident's personal email cannot be used as an admin position email. " +
                    "Position email: " + positionEmail,
                    HttpStatus.BAD_REQUEST);
        }

        assignmentRepo.findByPositionAndActiveTrue(position).ifPresent(existing -> {
            existing.setActive(false);
            existing.setEndDate(LocalDate.now());
            assignmentRepo.save(existing);
            log.info("Deactivated previous {} assignment (resident={})",
                    position.getDisplayName(), existing.getResident().getId());
        });

        Admin positionAdmin = adminRepo.findByEmail(positionEmail)
                .orElseGet(() -> {
                    Admin a = Admin.builder()
                            .name(POSITION_DISPLAY.get(position))
                            .email(positionEmail)
                            // Temporary password — will be set/reset below
                            .password(passwordEncoder.encode(generateSecurePassword()))
                            .superAdmin(position == Member.Position.PRESIDENT)
                            .forcePasswordChange(true)
                            .position(position)
                            .build();
                    Admin saved = adminRepo.save(a);
                    log.info("Created position-based admin account: {}", positionEmail);
                    return saved;
                });

        String generatedPassword = null;
        if (req.isResetPassword()) {
            generatedPassword = generateSecurePassword();
            positionAdmin.setPassword(passwordEncoder.encode(generatedPassword));
            positionAdmin.setForcePasswordChange(true);
        }

        if (position == Member.Position.PRESIDENT) {
            // Remove superAdmin from all other admin accounts
            adminRepo.findFirstBySuperAdminTrue().ifPresent(prev -> {
                if (!prev.getId().equals(positionAdmin.getId())) {
                    prev.setSuperAdmin(false);
                    adminRepo.save(prev);
                    log.info("Removed superAdmin from previous president account: {}", prev.getEmail());
                }
            });
            positionAdmin.setSuperAdmin(true);
        } else {
            // Non-president position accounts are regular admins
            positionAdmin.setSuperAdmin(false);
        }

        positionAdmin.setResidentId(resident.getId());
        adminRepo.save(positionAdmin);

        AdminAssignment assignment = AdminAssignment.builder()
                .resident(resident)
                .admin(positionAdmin)
                .position(position)
                .startDate(req.getStartDate() != null ? req.getStartDate() : LocalDate.now())
                .active(true)
                .notes(req.getNotes())
                .build();
        AdminAssignment saved = assignmentRepo.save(assignment);

        memberRepo.findByPosition(position).ifPresent(member -> {
            member.setResident(resident);
            member.setName(resident.getFullName());
            if (member.getPhoneNumber() == null || member.getPhoneNumber().isBlank()) {
                member.setPhoneNumber(resident.getPhone());
            }
            member.setEmail(positionEmail);
            member.setPlaceholder(false);
            memberRepo.save(member);
            log.info("Synced Member record for position {}", position.getDisplayName());
        });

        log.info("Appointed resident {} ({}) as {} via account {}",
                resident.getId(), resident.getFullName(), position.getDisplayName(), positionEmail);

        return AdminAssignmentDTO.AppointResponse.builder()
                .assignment(AdminAssignmentDTO.Response.from(saved))
                .positionEmail(positionEmail)
                .generatedPassword(generatedPassword)
                .message(resident.getFullName() + " appointed as " + position.getDisplayName() +
                         " using account " + positionEmail)
                .build();
    }

    @Transactional
    public AdminAssignmentDTO.Response revoke(AdminAssignmentDTO.RevokeRequest req) {
        AdminAssignment assignment = assignmentRepo.findById(req.getAssignmentId())
                .orElseThrow(() -> new CustomException(
                        "Assignment not found: " + req.getAssignmentId(), HttpStatus.NOT_FOUND));

        if (!assignment.isActive()) {
            throw new CustomException("Assignment is already inactive", HttpStatus.BAD_REQUEST);
        }

        assignment.setActive(false);
        assignment.setEndDate(req.getEndDate() != null ? req.getEndDate() : LocalDate.now());
        if (req.getNotes() != null) assignment.setNotes(req.getNotes());
        assignmentRepo.save(assignment);

        Admin positionAdmin = assignment.getAdmin();
        positionAdmin.setResidentId(null);
        adminRepo.save(positionAdmin);

        memberRepo.findByPosition(assignment.getPosition()).ifPresent(member -> {
            member.setResident(null);
            member.setName(assignment.getPosition().getDisplayName() + " (Vacant)");
            member.setPhotoUrl(null);
            member.setPhoneNumber(null);
            member.setEmail(null);
            member.setPlaceholder(true);
            memberRepo.save(member);
        });

        log.info("Revoked {} assignment from resident {}",
                assignment.getPosition().getDisplayName(), assignment.getResident().getId());

        return AdminAssignmentDTO.Response.from(assignment);
    }

    @Transactional
    public AdminAssignmentDTO.AppointResponse transferPresidency(
            Long newPresidentResidentId, String newPresidentNotes) {

        AdminAssignmentDTO.AppointRequest req = new AdminAssignmentDTO.AppointRequest();
        req.setResidentId(newPresidentResidentId);
        req.setPosition("PRESIDENT");
        req.setNotes(newPresidentNotes);
        req.setResetPassword(false);  // keep existing president account password

        return appoint(req);
    }

    @Transactional
    public void seedPositionAdminAccounts() {
        for (Member.Position position : Member.Position.values()) {
            String email = POSITION_EMAILS.get(position);
            if (email == null) continue;

            if (!adminRepo.existsByEmail(email)) {
                String tempPassword = generateSecurePassword();
                Admin a = Admin.builder()
                        .name(POSITION_DISPLAY.get(position) + " Account")
                        .email(email)
                        .password(passwordEncoder.encode(tempPassword))
                        .superAdmin(position == Member.Position.PRESIDENT)
                        .forcePasswordChange(true)
                        .position(position)
                        .build();
                adminRepo.save(a);
                log.info("Seeded position admin account: {} (temp pwd logged once — change immediately)", email);
                // Log temp password ONCE so admin can do initial login
                log.warn("INITIAL CREDENTIALS [{}]: email={} password={} — CHANGE IMMEDIATELY",
                        position.getDisplayName(), email, tempPassword);
            }
        }
    }

    private Member.Position parsePosition(String pos) {
        try {
            return Member.Position.valueOf(pos.toUpperCase());
        } catch (Exception e) {
            throw new CustomException("Invalid position: " + pos, HttpStatus.BAD_REQUEST);
        }
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$!";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}