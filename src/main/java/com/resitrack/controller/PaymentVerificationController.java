package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.PaymentVerificationRequestDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Resident;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.PaymentVerificationService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentVerificationController {

    private final PaymentVerificationService verificationService;
    private final ResidentService            residentService;
    private final AdminRepository            adminRepo;

    // ── User: get active admins for CASH payment selection ───────────────

    /**
     * Returns all active admin accounts for the "Paid To" dropdown in CASH payments.
     * Accessible by USER role so owners/family members can select the admin.
     */
    @GetMapping("/user/payment-verification/active-admins")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActiveAdmins() {
        List<Map<String, Object>> admins = adminRepo.findAll()
                .stream()
                .filter(a -> a.getResidentId() != null || a.isSuperAdmin())
                // Show admins who are assigned (have a residentId) or are super admin
                // This includes all position-based admins currently active
                .map(a -> {
                    String displayName = a.getName();
                    String position = a.getPosition() != null
                            ? a.getPosition().getDisplayName() : null;
                    Map<String, Object> entry = new java.util.HashMap<>();
                    entry.put("id",       a.getId());
                    entry.put("name",     displayName);
                    entry.put("position", position != null ? position : "");
                    return entry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(admins));
    }

    // ── User: GPAY — existing submit (unchanged) ──────────────────────────

    @PostMapping(value = "/user/payment-verification/submit",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitRequest(
            @RequestParam("name")           String name,
            @RequestParam("phoneNumber")    String phoneNumber,
            @RequestParam("paymentAmount")  BigDecimal paymentAmount,
            @RequestParam("transactionId")  String transactionId,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot,
            Authentication auth) throws IOException {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        PaymentVerificationRequestDTO result = verificationService.submitRequest(
                owner.getId(), r.getId(), name, phoneNumber, paymentAmount, transactionId, screenshot);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment verification request submitted successfully", result));
    }

    // ── User: CASH — new submit ───────────────────────────────────────────

    @PostMapping(value = "/user/payment-verification/submit-cash",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitCashRequest(
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        String     name          = (String) body.get("name");
        String     phoneNumber   = (String) body.get("phoneNumber");
        BigDecimal paymentAmount = new BigDecimal(body.get("paymentAmount").toString());
        Long       adminId       = Long.valueOf(body.get("paidToAdminId").toString());

        PaymentVerificationRequestDTO result = verificationService.submitCashRequest(
                owner.getId(), r.getId(), name, phoneNumber, paymentAmount, adminId);
        return ResponseEntity.ok(ApiResponse.success(
                "Cash payment request submitted. Admin will verify shortly.", result));
    }

    // ── User: BANK_TRANSFER — new submit ─────────────────────────────────

    @PostMapping(value = "/user/payment-verification/submit-bank-transfer",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitBankTransferRequest(
            @RequestParam("name")                          String name,
            @RequestParam("phoneNumber")                   String phoneNumber,
            @RequestParam("paymentAmount")                 BigDecimal paymentAmount,
            @RequestParam("referenceId")                   String referenceId,
            @RequestParam(value = "bankName",  required = false) String bankName,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot,
            Authentication auth) throws IOException {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        PaymentVerificationRequestDTO result = verificationService.submitBankTransferRequest(
                owner.getId(), r.getId(), name, phoneNumber, paymentAmount, referenceId, bankName, screenshot);
        return ResponseEntity.ok(ApiResponse.success(
                "Bank transfer details submitted. Admin will verify shortly.", result));
    }

    // ── User: list own requests ───────────────────────────────────────────

    @GetMapping("/user/payment-verification/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<PaymentVerificationRequestDTO>>> getMyRequests(
            Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        return ResponseEntity.ok(ApiResponse.success(
                verificationService.getResidentRequests(owner.getId())));
    }

    // ── Admin: list all requests ──────────────────────────────────────────

    @GetMapping("/admin/payment-verification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentVerificationRequestDTO>>> getAllRequests(
            @RequestParam(required = false) String status) {
        List<PaymentVerificationRequestDTO> list = (status != null && !status.isBlank())
                ? verificationService.getRequestsByStatus(status)
                : verificationService.getAllRequests();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/admin/payment-verification/pending-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", verificationService.getPendingCount())));
    }

    @PutMapping("/admin/payment-verification/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> verify(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment verified successfully", verificationService.verifyRequest(id)));
    }

    @PutMapping("/admin/payment-verification/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Payment request rejected", verificationService.rejectRequest(id, reason)));
    }

    @GetMapping("/admin/payment-verification/{id}/screenshot")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> getScreenshot(@PathVariable Long id) {
        Path path = verificationService.getScreenshotPath(id);
        Resource resource = new PathResource(path);
        if (!resource.exists()) {
            // The DB record and its screenshot_path are present (otherwise
            // verificationService.getScreenshotPath() would already have
            // thrown above) — the file itself is missing from disk at the
            // resolved path. On Render, this happens when the service has
            // no persistent Disk attached: every redeploy/restart starts
            // from a fresh container filesystem, discarding anything
            // previously written to app.upload.dir. See README/deployment
            // notes for the fix (attach a Render Disk + set UPLOAD_DIR).
            log.warn("Payment screenshot file missing on disk for request {} — expected at {}",
                    id, path.toAbsolutePath());
            throw new CustomException(
                    "This payment screenshot is no longer available on the server. " +
                    "It may have been uploaded before the most recent deployment and the " +
                    "file storage was not persistent across that restart. " +
                    "Ask the resident to re-upload the payment proof.",
                    HttpStatus.NOT_FOUND);
        }

        String filename = path.getFileName().toString().toLowerCase();
        MediaType mediaType = filename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
}