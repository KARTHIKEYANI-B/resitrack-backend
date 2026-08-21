package com.resitrack.service;

import com.resitrack.dto.VitalReadingDTO;
import com.resitrack.entity.Resident;
import com.resitrack.entity.VitalReading;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.VitalReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Sugar Level / BP Level readings for the currently authenticated
 * resident (owner or family member — personal, not property-scoped). Purely
 * a manual record store — no automated diagnosis or advice is generated here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VitalReadingService {

    private final VitalReadingRepository repo;
    private final ResidentRepository     residentRepo;

    public List<VitalReadingDTO.Response> getMine(Long residentId, String readingType) {
        String type = normalizeType(readingType);
        return repo.findByResidentIdAndReadingTypeAndActiveTrueOrderByReadingDateDescReadingTimeDesc(residentId, type)
                .stream()
                .map(VitalReadingDTO.Response::from)
                .toList();
    }

    @Transactional
    public VitalReadingDTO.Response add(Long residentId, VitalReadingDTO.Request req) {
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        String type = normalizeType(req.getReadingType());

        VitalReading e = VitalReading.builder()
                .resident(owner)
                .readingType(type)
                .readingDate(req.getReadingDate())
                .readingTime(req.getReadingTime())
                .sugarValue(type.equals("SUGAR") ? req.getSugarValue() : null)
                .sugarContext(type.equals("SUGAR") ? blankToNull(req.getSugarContext()) : null)
                .systolic(type.equals("BP") ? req.getSystolic() : null)
                .diastolic(type.equals("BP") ? req.getDiastolic() : null)
                .pulse(type.equals("BP") ? req.getPulse() : null)
                .notes(blankToNull(req.getNotes()))
                .active(true)
                .build();

        VitalReading saved = repo.save(e);
        log.info("Vital reading ({}) added for resident {}", type, residentId);
        return VitalReadingDTO.Response.from(saved);
    }

    @Transactional
    public VitalReadingDTO.Response update(Long id, Long residentId, VitalReadingDTO.Request req) {
        VitalReading e = findAndVerifyOwner(id, residentId);
        String type = normalizeType(req.getReadingType());

        e.setReadingType(type);
        e.setReadingDate(req.getReadingDate());
        e.setReadingTime(req.getReadingTime());
        e.setSugarValue(type.equals("SUGAR") ? req.getSugarValue() : null);
        e.setSugarContext(type.equals("SUGAR") ? blankToNull(req.getSugarContext()) : null);
        e.setSystolic(type.equals("BP") ? req.getSystolic() : null);
        e.setDiastolic(type.equals("BP") ? req.getDiastolic() : null);
        e.setPulse(type.equals("BP") ? req.getPulse() : null);
        e.setNotes(blankToNull(req.getNotes()));

        VitalReading saved = repo.save(e);
        log.info("Vital reading updated: {}", saved.getId());
        return VitalReadingDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        VitalReading e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("Vital reading soft-deleted: {}", id);
    }

    private VitalReading findAndVerifyOwner(Long id, Long residentId) {
        VitalReading e = repo.findById(id)
                .orElseThrow(() -> new CustomException("Reading not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this reading does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Reading has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private String normalizeType(String raw) {
        String upper = raw == null ? "" : raw.trim().toUpperCase();
        if (!upper.equals("SUGAR") && !upper.equals("BP")) {
            throw new CustomException("Reading type must be SUGAR or BP", HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
