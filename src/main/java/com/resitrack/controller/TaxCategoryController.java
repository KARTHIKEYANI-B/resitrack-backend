package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.TaxCategoryDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ResidentService;
import com.resitrack.service.TaxCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/tax-categories")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class TaxCategoryController {

    private final TaxCategoryService taxCategoryService;
    private final ResidentService    residentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxCategoryDTO.Response>>> getMyTaxCategories(Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(taxCategoryService.getMyTaxCategories(owner.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxCategoryDTO.Response>> getTaxCategory(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(taxCategoryService.getById(id, owner.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaxCategoryDTO.Response>> addTaxCategory(
            @RequestBody TaxCategoryDTO.Request req, Authentication auth) {
        Resident owner = getOwner(auth);
        TaxCategoryDTO.Response created = taxCategoryService.addTaxCategory(owner.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tax category added successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxCategoryDTO.Response>> updateTaxCategory(
            @PathVariable Long id, @RequestBody TaxCategoryDTO.Request req, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("Tax category updated",
                taxCategoryService.updateTaxCategory(id, owner.getId(), req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeTaxCategory(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        taxCategoryService.removeTaxCategory(id, owner.getId());
        return ResponseEntity.ok(ApiResponse.success("Tax category removed", null));
    }

    private Resident getOwner(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        if (r.getResidentRole() != Resident.ResidentRole.OWNER) {
            throw new com.resitrack.exception.CustomException(
                    "Only property owners can manage tax categories",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return r;
    }
}
