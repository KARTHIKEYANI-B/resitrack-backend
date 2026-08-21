package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.PersonalReminderDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.PersonalReminderService;
import com.resitrack.service.ResidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Personal Management — Reminders. Ownership is always resolved from the
 * JWT-backed {@link Authentication} principal, never from a client-supplied
 * id, so a resident can only ever read or write their own reminders.
 */
@RestController
@RequestMapping("/user/reminders")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class PersonalReminderController {

    private final PersonalReminderService reminderService;
    private final ResidentService         residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PersonalReminderDTO.Response>>> getMine(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(reminderService.getMine(r.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonalReminderDTO.Response>> add(
            @Valid @RequestBody PersonalReminderDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        PersonalReminderDTO.Response created = reminderService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reminder added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalReminderDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody PersonalReminderDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Reminder updated",
                reminderService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        reminderService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Reminder removed", null));
    }
}
