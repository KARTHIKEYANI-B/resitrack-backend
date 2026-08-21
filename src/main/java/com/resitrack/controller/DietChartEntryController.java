package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.DietChartEntryDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.DietChartEntryService;
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
 * Personal Management — Medical: Diet Chart. Ownership is always resolved
 * from the JWT-backed {@link Authentication} principal, never from a
 * client-supplied id, so a resident can only ever read or write their own
 * diet chart entries.
 */
@RestController
@RequestMapping("/user/medical/diet-chart")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class DietChartEntryController {

    private final DietChartEntryService dietService;
    private final ResidentService       residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DietChartEntryDTO.Response>>> getMine(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(dietService.getMine(r.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DietChartEntryDTO.Response>> add(
            @Valid @RequestBody DietChartEntryDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        DietChartEntryDTO.Response created = dietService.add(r.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Diet chart entry added", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DietChartEntryDTO.Response>> update(
            @PathVariable Long id, @Valid @RequestBody DietChartEntryDTO.Request req, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Diet chart entry updated",
                dietService.update(id, r.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id, Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        dietService.remove(id, r.getId());
        return ResponseEntity.ok(ApiResponse.success("Diet chart entry removed", null));
    }
}
