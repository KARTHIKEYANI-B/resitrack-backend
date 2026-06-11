package com.resitrack.service;

import com.resitrack.dto.ComplaintRequest;
import com.resitrack.dto.ComplaintResponseDTO;
import com.resitrack.entity.Complaint;
import com.resitrack.entity.Notification;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ComplaintRepository;
import com.resitrack.repository.NotificationRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository    complaintRepo;
    private final NotificationRepository notifRepo;
    private final ResidentRepository     residentRepo;

    @Transactional
    public ComplaintResponseDTO submitComplaint(Long residentId, ComplaintRequest req) {

        String title = req.getTitle() != null ? req.getTitle().trim() : "";
        String desc  = req.getDescription() != null ? req.getDescription().trim() : "";

        if (title.isBlank())
            throw new CustomException("Complaint title/subject is required", HttpStatus.BAD_REQUEST);
        if (desc.isBlank())
            throw new CustomException("Complaint description/message is required", HttpStatus.BAD_REQUEST);

        Resident resident = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        Complaint complaint = Complaint.builder()
                .title(title)
                .description(desc)
                .residentId(residentId)
                .residentName(resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .flatType(resident.getFlatType())
                .status(Complaint.ComplaintStatus.OPEN)
                .build();
        complaint = complaintRepo.save(complaint);

        String dateStr = complaint.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));

        String notifMessage = String.format(
                "New complaint from %s (Flat %s%s):\n\"%s\"\nSubmitted: %s | Status: OPEN",
                resident.getFullName(),
                resident.getFlatNumber(),
                resident.getFlatType() != null ? " – " + resident.getFlatType() : "",
                desc,
                dateStr
        );

        Notification adminNotif = Notification.builder()
                .title("Complaint: " + title)
                .message(notifMessage)
                .type(Notification.NotificationType.COMPLAINT)
                .targetResidentId(residentId)
                .residentName(resident.getFullName())
                .flatNumber(resident.getFlatNumber())
                .recipientRole("ADMIN")
                .isRead(false)
                .build();
        adminNotif = notifRepo.save(adminNotif);

        complaint.setNotificationId(adminNotif.getId());
        complaint = complaintRepo.save(complaint);

        return ComplaintResponseDTO.from(complaint);
    }


    public List<ComplaintResponseDTO> getAllComplaints() {
        return complaintRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(ComplaintResponseDTO::from).collect(Collectors.toList());
    }

    public List<ComplaintResponseDTO> getComplaintsByStatus(Complaint.ComplaintStatus status) {
        return complaintRepo.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(ComplaintResponseDTO::from).collect(Collectors.toList());
    }

    public List<ComplaintResponseDTO> getComplaintsForResident(Long residentId) {
        return complaintRepo.findByResidentIdOrderByCreatedAtDesc(residentId)
                .stream().map(ComplaintResponseDTO::from).collect(Collectors.toList());
    }

    @Transactional
    public ComplaintResponseDTO updateStatus(Long complaintId, String newStatus, String adminReply) {
        Complaint c = complaintRepo.findById(complaintId)
                .orElseThrow(() -> new CustomException("Complaint not found: " + complaintId, HttpStatus.NOT_FOUND));
        try {
            c.setStatus(Complaint.ComplaintStatus.valueOf(newStatus.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new CustomException("Invalid status: " + newStatus, HttpStatus.BAD_REQUEST);
        }
        if (adminReply != null && !adminReply.isBlank())
            c.setAdminReply(adminReply.trim());
        return ComplaintResponseDTO.from(complaintRepo.save(c));
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",      complaintRepo.count());
        stats.put("open",       complaintRepo.countByStatus(Complaint.ComplaintStatus.OPEN));
        stats.put("inProgress", complaintRepo.countByStatus(Complaint.ComplaintStatus.IN_PROGRESS));
        stats.put("resolved",   complaintRepo.countByStatus(Complaint.ComplaintStatus.RESOLVED));
        stats.put("closed",     complaintRepo.countByStatus(Complaint.ComplaintStatus.CLOSED));
        return stats;
    }
}