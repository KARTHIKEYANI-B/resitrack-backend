package com.resitrack.service;

import com.resitrack.dto.DietChartEntryDTO;
import com.resitrack.entity.DietChartEntry;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.DietChartEntryRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Diet Chart entries for the currently authenticated resident
 * (owner or family member — personal, not property-scoped). Purely a manual
 * record store — no automated nutrition advice is generated here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DietChartEntryService {

    private final DietChartEntryRepository repo;
    private final ResidentRepository       residentRepo;

    public List<DietChartEntryDTO.Response> getMine(Long residentId) {
        return repo.findByResidentIdAndActiveTrueOrderByCreatedAtDesc(residentId)
                .stream()
                .map(DietChartEntryDTO.Response::from)
                .toList();
    }

    @Transactional
    public DietChartEntryDTO.Response add(Long residentId, DietChartEntryDTO.Request req) {
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        DietChartEntry e = DietChartEntry.builder()
                .resident(owner)
                .title(blankToNull(req.getTitle()))
                .mealType(blankToNull(req.getMealType()))
                .description(req.getDescription().trim())
                .notes(blankToNull(req.getNotes()))
                .active(true)
                .build();

        DietChartEntry saved = repo.save(e);
        log.info("Diet chart entry added for resident {}", residentId);
        return DietChartEntryDTO.Response.from(saved);
    }

    @Transactional
    public DietChartEntryDTO.Response update(Long id, Long residentId, DietChartEntryDTO.Request req) {
        DietChartEntry e = findAndVerifyOwner(id, residentId);

        e.setTitle(blankToNull(req.getTitle()));
        e.setMealType(blankToNull(req.getMealType()));
        e.setDescription(req.getDescription().trim());
        e.setNotes(blankToNull(req.getNotes()));

        DietChartEntry saved = repo.save(e);
        log.info("Diet chart entry updated: {}", saved.getId());
        return DietChartEntryDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        DietChartEntry e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("Diet chart entry soft-deleted: {}", id);
    }

    private DietChartEntry findAndVerifyOwner(Long id, Long residentId) {
        DietChartEntry e = repo.findById(id)
                .orElseThrow(() -> new CustomException("Diet chart entry not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this entry does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Entry has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
