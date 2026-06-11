package com.resitrack.service;

import com.resitrack.entity.Resident;
import com.resitrack.entity.Resident.RegistrationStatus;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResidentApprovalService {

    private final ResidentRepository  residentRepo;
    private final NotificationService notificationService;

    public List<Resident> getPendingRegistrations() {
        return residentRepo.findByRegistrationStatusOrderByCreatedAtDesc(RegistrationStatus.PENDING);
    }

    public List<Resident> getAllRegistrations(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return residentRepo.findAllByOrderByCreatedAtDesc();
        }
        RegistrationStatus rs = RegistrationStatus.valueOf(status.toUpperCase());
        return residentRepo.findByRegistrationStatusOrderByCreatedAtDesc(rs);
    }

    public long getPendingCount() {
        return residentRepo.countByRegistrationStatus(RegistrationStatus.PENDING);
    }

    @Transactional
    public Resident approve(Long residentId, Long adminId) {
        Resident r = getOrThrow(residentId);

        r.setRegistrationStatus(RegistrationStatus.APPROVED);
        r.setApproved(true);
        r.setActive(true);
        r.setRegistered(true);
        r.setStatus(Resident.ResidentStatus.ACTIVE);
        r.setApprovedByAdminId(adminId);
        r.setApprovedAt(LocalDateTime.now());
        r.setRejectedReason(null);
        residentRepo.save(r);

        notificationService.notifyUserApproved(r);
        return r;
    }

    @Transactional
    public Resident reject(Long residentId, Long adminId, String reason) {
        if (reason == null || reason.isBlank())
            throw new CustomException("Rejection reason is required", HttpStatus.BAD_REQUEST);

        Resident r = getOrThrow(residentId);

        r.setRegistrationStatus(RegistrationStatus.REJECTED);
        r.setApproved(false);
        r.setActive(false);
        r.setStatus(Resident.ResidentStatus.INACTIVE);
        r.setApprovedByAdminId(adminId);
        r.setApprovedAt(LocalDateTime.now());
        r.setRejectedReason(reason.trim());
        residentRepo.save(r);

        notificationService.notifyUserRejected(r);
        return r;
    }

    @Transactional
    public int bulkApprove(List<Long> ids, Long adminId) {
        int count = 0;
        for (Long id : ids) {
            try {
                approve(id, adminId);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    @Transactional
    public int bulkReject(List<Long> ids, Long adminId, String reason) {
        if (reason == null || reason.isBlank())
            throw new CustomException("Rejection reason is required for bulk reject", HttpStatus.BAD_REQUEST);

        int count = 0;
        for (Long id : ids) {
            try {
                reject(id, adminId, reason);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    private Resident getOrThrow(Long id) {
        return residentRepo.findById(id)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));
    }
}
