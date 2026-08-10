package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Self-service signature/seal image for the CURRENTLY AUTHENTICATED
 * admin/superadmin — not a shared, association-wide setting. Whatever is
 * set here is stamped onto every receipt this admin subsequently
 * approves/verifies/records (see PaymentService/PaymentVerificationService's
 * generateReceipt(), which snapshots signatureUrl onto Receipt.adminSignature
 * at the moment the receipt is generated — so it stays what it was on that
 * receipt even if the admin later changes their signature).
 */
@RestController
@RequestMapping("/admin/signature")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSignatureController {

    private final AdminRepository   adminRepo;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getMySignature(Authentication auth) {
        Admin admin = requireAdmin(auth);
        Map<String, String> body = new java.util.HashMap<>();
        body.put("signatureUrl", admin.getSignatureUrl());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadMySignature(
            @RequestParam("file") MultipartFile file, Authentication auth) {
        Admin admin = requireAdmin(auth);

        CloudinaryService.UploadResult result = cloudinaryService.upload(file, "resitrack/signatures");

        // Single-current-signature: replacing an existing one deletes the
        // old Cloudinary asset rather than keeping a gallery of past uploads.
        String oldPublicId = admin.getSignaturePublicId();

        admin.setSignatureUrl(result.secureUrl());
        admin.setSignaturePublicId(result.publicId());
        adminRepo.save(admin);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryService.delete(oldPublicId);
        }

        Map<String, String> body = new java.util.HashMap<>();
        body.put("signatureUrl", result.secureUrl());
        return ResponseEntity.ok(ApiResponse.success("Signature updated", body));
    }

    private Admin requireAdmin(Authentication auth) {
        return adminRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.UNAUTHORIZED));
    }
}
