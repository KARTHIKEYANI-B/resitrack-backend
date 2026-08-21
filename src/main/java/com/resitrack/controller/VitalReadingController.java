package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.VitalReadingDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ResidentService;
import com.resitrack.service.VitalReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Personal Management — Medical: Sugar Level / BP Level readings. Ownership
 * is always resolved from the JWT-backed {@link Authentication} principal,
 * never from a client-supplied id, so a resident can only ever read or write
 * their own readings.
 */
@RestController
@RequestMapping("/user/medical/vitals")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class VitalReadingController {

    private final VitalReadingService vitalService;
    private final ResidentService     residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VitalReadingDTO.Response>>> getMine(
            @RequestParam String type, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(vitalService.getMine(r.getId(), type)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VitalReadingDTO.Response>> add(
            @Valid @RequestBody VitalReadingDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        VitalReadingDTO.Response created = vitalService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reading added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VitalReadingDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody VitalReadingDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Reading updated",
                vitalService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        vitalService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Reading removed", null));
    }
}
