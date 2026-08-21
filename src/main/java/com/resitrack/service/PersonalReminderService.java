package com.resitrack.service;

import com.resitrack.dto.PersonalReminderDTO;
import com.resitrack.entity.PersonalReminder;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.PersonalReminderRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages manually-created personal reminders for the currently
 * authenticated resident (owner or family member — personal, not
 * property-scoped). Delivery of due reminders happens through the existing
 * {@link ReminderSchedulerService} single scheduled entry point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalReminderService {

    private final PersonalReminderRepository repo;
    private final ResidentRepository         residentRepo;

    public List<PersonalReminderDTO.Response> getMine(Long residentId) {
        return repo.findByResidentIdAndActiveTrueOrderByReminderDateAsc(residentId)
                .stream()
                .map(PersonalReminderDTO.Response::from)
                .toList();
    }

    @Transactional
    public PersonalReminderDTO.Response add(Long residentId, PersonalReminderDTO.Request req) {
        Resident owner = residentRepo.findById(residentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        PersonalReminder e = PersonalReminder.builder()
                .resident(owner)
                .title(req.getTitle().trim())
                .category(blankToNull(req.getCategory()))
                .reminderDate(req.getReminderDate())
                .notes(blankToNull(req.getNotes()))
                .completed(Boolean.TRUE.equals(req.getCompleted()))
                .active(true)
                .build();

        PersonalReminder saved = repo.save(e);
        log.info("Personal reminder added for resident {}", residentId);
        return PersonalReminderDTO.Response.from(saved);
    }

    @Transactional
    public PersonalReminderDTO.Response update(Long id, Long residentId, PersonalReminderDTO.Request req) {
        PersonalReminder e = findAndVerifyOwner(id, residentId);

        e.setTitle(req.getTitle().trim());
        e.setCategory(blankToNull(req.getCategory()));
        e.setReminderDate(req.getReminderDate());
        e.setNotes(blankToNull(req.getNotes()));
        if (req.getCompleted() != null) e.setCompleted(req.getCompleted());

        PersonalReminder saved = repo.save(e);
        log.info("Personal reminder updated: {}", saved.getId());
        return PersonalReminderDTO.Response.from(saved);
    }

    @Transactional
    public void remove(Long id, Long residentId) {
        PersonalReminder e = findAndVerifyOwner(id, residentId);
        e.setActive(false);
        repo.save(e);
        log.info("Personal reminder soft-deleted: {}", id);
    }

    private PersonalReminder findAndVerifyOwner(Long id, Long residentId) {
        PersonalReminder e = repo.findById(id)
                .orElseThrow(() -> new CustomException("Reminder not found", HttpStatus.NOT_FOUND));
        if (!e.getResident().getId().equals(residentId)) {
            throw new CustomException("Access denied: this reminder does not belong to you",
                    HttpStatus.FORBIDDEN);
        }
        if (!e.isActive()) {
            throw new CustomException("Reminder has been removed", HttpStatus.NOT_FOUND);
        }
        return e;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
