package com.resitrack.controller;

import com.resitrack.dto.*;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.service.PopulationService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/residents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ResidentController {

    private final ResidentService        residentService;
    private final PopulationService      populationService;
    private final FamilyMemberRepository familyMemberRepo;

    @GetMapping
    public ResponseEntity<List<ResidentDTO>> getAll() {
        return ResponseEntity.ok(residentService.getAllResidents());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ResidentDTO>> getActive() {
        return ResponseEntity.ok(residentService.getActiveResidents());
    }

    @GetMapping("/population")
    public ResponseEntity<ApiResponse<PopulationStatsDTO>> getPopulation() {
        return ResponseEntity.ok(ApiResponse.success(populationService.getPopulationStats()));
    }

    @GetMapping("/population/age-range")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAgeRangeCount(
            @RequestParam(defaultValue = "0")   int minAge,
            @RequestParam(defaultValue = "150") int maxAge) {

        if (minAge < 0)      minAge = 0;
        if (maxAge < minAge) maxAge = minAge;

        long count = familyMemberRepo.countByAgeRange(minAge, maxAge);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", count, "minAge", minAge, "maxAge", maxAge)));
    }

    @GetMapping("/population/age-range/members")
    public ResponseEntity<ApiResponse<List<FamilyMemberSummaryDTO>>> getFamilyMembersByAgeRange(
            @RequestParam(defaultValue = "0")   int minAge,
            @RequestParam(defaultValue = "150") int maxAge) {

        if (minAge < 0)      minAge = 0;
        if (maxAge < minAge) maxAge = minAge;

        List<FamilyMemberSummaryDTO> members =
                residentService.getFamilyMembersByAgeRange(minAge, maxAge);

        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @GetMapping("/{id}/family-members")
    public ResponseEntity<ApiResponse<List<FamilyMemberSummaryDTO>>> getFamilyMembers(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                residentService.getFamilyMembersForOwners(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ResidentDTO>> updateResident(
            @PathVariable Long id, @RequestBody ResidentDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Updated", residentService.updateResident(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteResident(@PathVariable Long id) {
        residentService.deleteResident(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}