package com.resitrack.service;

import com.resitrack.dto.AdminAssignmentDTO;
import com.resitrack.dto.MemberDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Member;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.MemberRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository      memberRepo;
    private final AdminRepository       adminRepo;
    private final ResidentRepository    residentRepo;
    private final PasswordEncoder       passwordEncoder;

    @Lazy
    private final AdminAssignmentService assignmentService;

    public List<MemberDTO.Response> getAllMembers() {
        return memberRepo.findAllActiveOrdered()
                .stream()
                .map(MemberDTO.Response::from)
                .toList();
    }

    public MemberDTO.Response getMemberById(Long id) {
        Member m = memberRepo.findById(id)
                .orElseThrow(() -> new CustomException("Member not found", HttpStatus.NOT_FOUND));
        return MemberDTO.Response.from(m);
    }

    @Transactional
    public MemberDTO.Response createMember(MemberDTO.Request req) {
        Member.Position position = parsePosition(req.getPosition());

        memberRepo.findByPosition(position).ifPresent(existing -> {
            if (!existing.isPlaceholder()) {
                throw new CustomException(
                        "Position " + position.getDisplayName() + " is already occupied",
                        HttpStatus.CONFLICT);
            }
        });

        Member member = buildMemberFromRequest(req, position, null);

        if (req.getResidentId() != null) {
            Resident resident = residentRepo.findById(req.getResidentId())
                    .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
            member.setResident(resident);
            member.setPlaceholder(false);
            if (member.getName() == null || member.getName().isBlank()) {
                member.setName(resident.getFullName());
            }
            if (member.getPhoneNumber() == null || member.getPhoneNumber().isBlank()) {
                member.setPhoneNumber(resident.getPhone());
            }
        }

        memberRepo.findByPosition(position).ifPresent(old -> {
            if (old.isPlaceholder()) memberRepo.delete(old);
        });

        Member saved = memberRepo.save(member);

        if (req.getResidentId() != null) {
            try {
                AdminAssignmentDTO.AppointRequest appointReq = new AdminAssignmentDTO.AppointRequest();
                appointReq.setResidentId(req.getResidentId());
                appointReq.setPosition(position.name());
                appointReq.setNotes("Appointed via Members List");
                appointReq.setResetPassword(false);
                assignmentService.appoint(appointReq);
            } catch (Exception e) {
                log.warn("Could not auto-create admin assignment for member {}: {}", saved.getId(), e.getMessage());
            }
        }

        // ── Task 2: If Super Admin provided a password, apply it to the position admin account ──
        applyAdminPasswordIfProvided(position, req.getAdminPassword());

        log.info("Member created: {} — {}", saved.getPosition().getDisplayName(), saved.getName());
        return MemberDTO.Response.from(saved);
    }

    @Transactional
    public MemberDTO.Response updateMember(Long id, MemberDTO.Request req) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new CustomException("Member not found", HttpStatus.NOT_FOUND));

        if (req.getName()        != null) member.setName(req.getName());
        if (req.getPhotoUrl()    != null) member.setPhotoUrl(req.getPhotoUrl());
        if (req.getPhoneNumber() != null) member.setPhoneNumber(req.getPhoneNumber());
        if (req.getJoinedDate()  != null) member.setJoinedDate(req.getJoinedDate());
        member.setActive(req.isActive());

        // Sync name/phone to linked admin account
        if (member.getResident() != null) {
            adminRepo.findByResidentId(member.getResident().getId()).ifPresent(admin -> {
                if (req.getName() != null) admin.setName(req.getName());
                if (req.getPhoneNumber() != null) admin.setPhone(req.getPhoneNumber());
                adminRepo.save(admin);
            });
        }

        Member saved = memberRepo.save(member);

        // ── Task 2: If Super Admin provided a password, apply it to the position admin account ──
        applyAdminPasswordIfProvided(member.getPosition(), req.getAdminPassword());

        log.info("Member updated: {} — {}", saved.getPosition().getDisplayName(), saved.getName());
        return MemberDTO.Response.from(saved);
    }

    /**
     * TASK 2 — Applies adminPassword to the position admin account if:
     *  - adminPassword is non-null and non-blank
     *  - the position has a corresponding admin account in the DB
     *
     * This is only reachable via MemberController which already enforces Super Admin.
     */
    private void applyAdminPasswordIfProvided(Member.Position position, String adminPassword) {
        if (adminPassword == null || adminPassword.isBlank()) return;
        if (adminPassword.length() < 6) {
            throw new CustomException(
                    "Admin account password must be at least 6 characters", HttpStatus.BAD_REQUEST);
        }

        String positionEmail = resolvePositionEmail(position);
        if (positionEmail == null) {
            log.warn("No position email mapping for {}, skipping password update", position);
            return;
        }

        adminRepo.findByEmail(positionEmail).ifPresent(admin -> {
            admin.setPassword(passwordEncoder.encode(adminPassword.trim()));
            admin.setForcePasswordChange(false);
            adminRepo.save(admin);
            log.info("Position admin password updated for: {} ({})",
                    position.getDisplayName(), positionEmail);
        });
    }

    /**
     * Returns the canonical login email for each committee position.
     *
     * TASK 1 + TASK 2: Must match AdminAssignmentService.POSITION_EMAILS exactly.
     * When position emails change in AdminAssignmentService, update this map too.
     */
    private String resolvePositionEmail(Member.Position position) {
        return switch (position) {
            case PRESIDENT       -> "superadmin@gmail.com";
            case VICE_PRESIDENT  -> "admin.vicepresident@apartment.com";
            case SECRETARY       -> "secretary@gmail.com";
            case JOINT_SECRETARY -> "joinsecratery@gmail.com";
            case TREASURER       -> "treasurer@gmail.com";
        };
    }

    @Transactional
    public void removeMember(Long id) {
        Member member = memberRepo.findById(id)
                .orElseThrow(() -> new CustomException("Member not found", HttpStatus.NOT_FOUND));

        if (member.getPosition() == Member.Position.PRESIDENT) {
            throw new CustomException(
                    "Cannot remove President directly. Use Transfer Presidency instead.",
                    HttpStatus.BAD_REQUEST);
        }

        if (member.getResident() != null) {
            try {
                assignmentService.getActiveAssignments().stream()
                        .filter(a -> a.getResidentId() != null &&
                                     a.getResidentId().equals(member.getResident().getId()) &&
                                     member.getPosition().name().equals(a.getPosition()))
                        .findFirst()
                        .ifPresent(a -> {
                            AdminAssignmentDTO.RevokeRequest revokeReq = new AdminAssignmentDTO.RevokeRequest();
                            revokeReq.setAssignmentId(a.getId());
                            revokeReq.setNotes("Removed via Members List");
                            assignmentService.revoke(revokeReq);
                        });
            } catch (Exception e) {
                log.warn("Could not revoke assignment for member {}: {}", id, e.getMessage());
            }
        }

        member.setResident(null);
        member.setName(member.getPosition().getDisplayName() + " (Vacant)");
        member.setPhotoUrl(null);
        member.setPhoneNumber(null);
        member.setEmail(null);
        member.setJoinedDate(null);
        member.setPlaceholder(true);
        memberRepo.save(member);

        log.info("Member removed from position: {}", member.getPosition().getDisplayName());
    }

    @Transactional
    public MemberDTO.Response transferPresidency(MemberDTO.TransferPresidencyRequest req) {
        Member currentPresident = memberRepo.findByPosition(Member.Position.PRESIDENT)
                .orElseThrow(() -> new CustomException("Current President not found", HttpStatus.NOT_FOUND));

        Member newPresident;
        Resident newPresidentResident;

        if (req.getNewPresidentMemberId() != null) {
            newPresident = memberRepo.findById(req.getNewPresidentMemberId())
                    .orElseThrow(() -> new CustomException("New president member not found", HttpStatus.NOT_FOUND));
            if (newPresident.getResident() == null)
                throw new CustomException("Selected member has no linked resident", HttpStatus.BAD_REQUEST);
            newPresidentResident = newPresident.getResident();
        } else if (req.getNewPresidentResidentId() != null) {
            newPresidentResident = residentRepo.findById(req.getNewPresidentResidentId())
                    .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
            newPresident = memberRepo.findByResidentId(newPresidentResident.getId())
                    .orElseThrow(() -> new CustomException(
                            "This resident is not a committee member. Assign them a position first.",
                            HttpStatus.BAD_REQUEST));
        } else {
            throw new CustomException("Must provide newPresidentMemberId or newPresidentResidentId",
                    HttpStatus.BAD_REQUEST);
        }

        if (newPresident.getId().equals(currentPresident.getId()))
            throw new CustomException("New president is same as current president", HttpStatus.BAD_REQUEST);

        Member.Position oldPositionOfNew = newPresident.getPosition();
        currentPresident.setPosition(oldPositionOfNew);
        memberRepo.save(currentPresident);
        newPresident.setPosition(Member.Position.PRESIDENT);
        memberRepo.save(newPresident);

        assignmentService.transferPresidency(newPresidentResident.getId(),
                "Presidency transferred from " + currentPresident.getName());

        log.info("Presidency transferred from {} to {}", currentPresident.getName(), newPresident.getName());
        return MemberDTO.Response.from(newPresident);
    }

    @Transactional
    public void seedDefaultPositions() {
        for (Member.Position pos : Member.Position.values()) {
            if (!memberRepo.existsByPosition(pos)) {
                Member placeholder = Member.builder()
                        .position(pos)
                        .name(pos.getDisplayName())
                        .active(true)
                        .placeholder(true)
                        .build();
                memberRepo.save(placeholder);
                log.info("Seeded placeholder for position: {}", pos.getDisplayName());
            }
        }
    }

    private Member buildMemberFromRequest(MemberDTO.Request req, Member.Position position, Resident resident) {
        return Member.builder()
                .resident(resident)
                .position(position)
                .name(req.getName() != null ? req.getName() : position.getDisplayName())
                .photoUrl(req.getPhotoUrl())
                .phoneNumber(req.getPhoneNumber())
                // email intentionally NOT set from req.getEmail() — position email used instead
                .joinedDate(req.getJoinedDate())
                .active(req.isActive())
                .placeholder(resident == null)
                .build();
    }

    private Member.Position parsePosition(String positionStr) {
        try {
            return Member.Position.valueOf(positionStr.toUpperCase());
        } catch (Exception e) {
            throw new CustomException("Invalid position: " + positionStr, HttpStatus.BAD_REQUEST);
        }
    }
}