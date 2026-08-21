package com.resitrack.service;

import com.resitrack.dto.DoctorAppointmentDTO;
import com.resitrack.entity.DoctorAppointment;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.DoctorAppointmentRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Doctor Appointments for the currently authenticated resident
 * (owner or family member — medical info is personal, not property-scoped).
 * Purely a manual record store — no diagnosis or advice is generated here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorAppointmentService {

    private final DoctorAppointmentRepository repo;
    private final ResidentRepository          residentRepo;

    public List<DoctorAppointmentDTO.Response> getMine(Long residentId) {
        return repo.findByResidentIdAndActiveTrueOrderByAppointmentDateDesc(residentId)
                .stream()
                .map(DoctorAppointmentDTO.Response::from)
                .toList();
    }

    public DoctorAppointmentDTO.Response getById(Long id, Long residentId) {
        return DoctorAppointmentDTO.Response.from(findAndVerifyOwner(id, residentId));
    }

    @Transactional
    public DoctorAppointmentDTO.Response add(Long residentId, DoctorAppointmentDTO.Request req) {
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        DoctorAppointment e = DoctorAppointment.builder()
                .resident(owner)
                .doctorName(req.getDoctorName().trim())
                .specialization(blankToNull(req.getSpecialization()))
                .hospitalClinic(blankToNull(req.getHospitalClinic()))
                .appointmentDate(req.getAppointmentDate())
                .appointmentTime(req.getAppointmentTime())
                .reason(blankToNull(req.getReason()))
                .notes(blankToNull(req.getNotes()))
                .status(req.getStatus() == null || req.getStatus().isBlank() ? "Scheduled" : req.getStatus().trim())
                .active(true)
                .build();

        DoctorAppointment saved = repo.save(e);
        log.info("Doctor appointment added for resident {}", residentId);
        return DoctorAppointmentDTO.Response.from(saved);
    }

    @Transactional
    public DoctorAppointmentDTO.Response update(Long id, Long residentId, DoctorAppointmentDTO.Request req) {
        DoctorAppointment e = findAndVerifyOwner(id, residentId);

        e.setDoctorName(req.getDoctorName().trim());
        e.setSpecialization(blankToNull(req.getSpecialization()));
        e.setHospitalClinic(blankToNull(req.getHospitalClinic()));
        e.setAppointmentDate(req.getAppointmentDate());
        e.setAppointmentTime(req.getAppointmentTime());
        e.setReason(blankToNull(req.getReason()));
        e.setNotes(blankToNull(req.getNotes()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) e.setStatus(req.getStatus().trim());

        DoctorAppointment saved = repo.save(e);
        log.info("Doctor appointment updated: {}", saved.getId());
        return DoctorAppointmentDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        DoctorAppointment e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("Doctor appointment soft-deleted: {}", id);
    }

    private DoctorAppointment findAndVerifyOwner(Long id, Long residentId) {
        DoctorAppointment e = repo.findById(id)
                .orElseThrow(() -> new CustomException("Appointment not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this appointment does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Appointment has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
