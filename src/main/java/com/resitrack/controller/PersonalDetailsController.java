package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.PersonalDetailsDTO;
import com.resitrack.dto.PersonalDetailsUpdateRequest;
import com.resitrack.entity.Resident;
import com.resitrack.service.ResidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Personal Management (Phase 1) — view/edit the authenticated resident's own
 * personal information. Ownership is always resolved from the JWT-backed
 * {@link Authentication} principal (email), never from a client-supplied id,
 * so a resident can only ever read or write their own record.
 */
@RestController
@RequestMapping("/user/personal-details")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class PersonalDetailsController {

    private final ResidentService residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PersonalDetailsDTO>> getPersonalDetails(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(residentService.toPersonalDetailsDTO(r)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PersonalDetailsDTO>> updatePersonalDetails(
            @Valid @RequestBody PersonalDetailsUpdateRequest req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Personal information updated",
                residentService.updatePersonalDetails(r.getId(), req)));
    }
}
