package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.FamilyMemberDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.FamilyMemberService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/family-members")
@RequiredArgsConstructor
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;
    private final ResidentService     residentService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<FamilyMemberDTO.Response>>> getMyFamilyMembers(
            Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(
                familyMemberService.getMyFamilyMembers(owner.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberDTO.Response>> getFamilyMember(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success(
                familyMemberService.getById(id, owner.getId())));
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberDTO.Response>> addFamilyMember(
            @RequestBody FamilyMemberDTO.Request req, Authentication auth) {
        Resident owner = getOwner(auth);
        FamilyMemberDTO.Response created =
                familyMemberService.addFamilyMember(owner.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Family member added successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberDTO.Response>> updateFamilyMember(
            @PathVariable Long id, @RequestBody FamilyMemberDTO.Request req,
            Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("Family member updated",
                familyMemberService.updateFamilyMember(id, owner.getId(), req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> removeFamilyMember(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        familyMemberService.removeFamilyMember(id, owner.getId());
        return ResponseEntity.ok(ApiResponse.success("Family member removed", null));
    }

    @PostMapping("/{id}/grant-access")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberDTO.Response>> grantAccess(
            @PathVariable Long id,
            @RequestBody FamilyMemberDTO.GrantAccessRequest req,
            Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("App access granted successfully",
                familyMemberService.grantAppAccess(id, owner.getId(), req)));
    }

    @PostMapping("/{id}/revoke-access")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberDTO.Response>> revokeAccess(
            @PathVariable Long id, Authentication auth) {
        Resident owner = getOwner(auth);
        return ResponseEntity.ok(ApiResponse.success("App access revoked",
                familyMemberService.revokeAppAccess(id, owner.getId())));
    }

    private Resident getOwner(Authentication auth) {
        Resident r = residentService.getByEmail(auth.getName());
        if (r.getResidentRole() != Resident.ResidentRole.OWNER) {
            throw new com.resitrack.exception.CustomException(
                    "Only property owners can manage family members",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return r;
    }
}