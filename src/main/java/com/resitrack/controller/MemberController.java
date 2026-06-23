package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.MemberDTO;
import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService   memberService;
    private final AdminRepository adminRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberDTO.Response>>> getAllMembers() {
        return ResponseEntity.ok(ApiResponse.success(memberService.getAllMembers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemberDTO.Response>> getMemberById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMemberById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemberDTO.Response>> createMember(
            @RequestBody MemberDTO.Request req, Authentication auth) {
        requireSuperAdmin(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member created successfully",
                        memberService.createMember(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MemberDTO.Response>> updateMember(
            @PathVariable Long id, @RequestBody MemberDTO.Request req, Authentication auth) {
        requireSuperAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success("Member updated successfully",
                memberService.updateMember(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id, Authentication auth) {
        requireSuperAdmin(auth);
        memberService.removeMember(id);
        return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
    }

    @PostMapping("/transfer-presidency")
    public ResponseEntity<ApiResponse<MemberDTO.Response>> transferPresidency(
            @RequestBody MemberDTO.TransferPresidencyRequest req, Authentication auth) {
        requireSuperAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success("Presidency transferred successfully",
                memberService.transferPresidency(req)));
    }

    private void requireSuperAdmin(Authentication auth) {
        String email = auth.getName();
        Admin admin = adminRepo.findByEmail(email)
                .orElseThrow(() -> new CustomException("Unauthorized", HttpStatus.FORBIDDEN));
        if (!isSuperAdminSafe(admin)) {
            throw new CustomException(
                    "Only SUPER_ADMIN can manage committee members", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isSuperAdminSafe(Admin admin) {
        try {
            return admin.isSuperAdmin();
        } catch (Exception e) {
            return false;
        }
    }
}