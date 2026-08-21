package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.FinancialYearMonthDTO;
import com.resitrack.dto.PaymentVerificationRequestDTO;
import com.resitrack.entity.Admin;
import com.resitrack.entity.Resident;
import com.resitrack.repository.AdminRepository;
import com.resitrack.service.PaymentVerificationService;
import com.resitrack.service.ResidentService;
import com.resitrack.util.ViewerGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
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
    private final ViewerGuard               viewerGuard;

    @GetMapping("/user/payment-verification/active-admins")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActiveAdmins() {
        List<Map<String, Object>> admins = adminRepo.findAll()
                .stream()
                .filter(a -> a.getResidentId() != null || a.isSuperAdmin())
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

    // ── Multi-Month Maintenance Payment ────────────────────────────────────

    /** Financial-Year month selector: status + payable amount for every FY month up to this one. */
    @GetMapping("/user/payment-verification/financial-year-months")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<FinancialYearMonthDTO>>> getFinancialYearMonths(
            Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        return ResponseEntity.ok(ApiResponse.success(
                verificationService.getFinancialYearMonths(owner.getId())));
    }

    @PostMapping(value = "/user/payment-verification/submit-multi",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitMultiMonthRequest(
            @RequestParam("name")           String name,
            @RequestParam("phoneNumber")    String phoneNumber,
            @RequestParam("months")         List<String> months,
            @RequestParam("transactionId")  String transactionId,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot,
            Authentication auth) throws IOException {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        PaymentVerificationRequestDTO result = verificationService.submitMultiMonthRequest(
                owner.getId(), r.getId(), name, phoneNumber, months, transactionId, screenshot);
        return ResponseEntity.ok(ApiResponse.success(
                "Multi-month payment submitted for admin verification", result));
    }

    @PostMapping(value = "/user/payment-verification/submit-multi-cash",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitMultiMonthCashRequest(
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        String       name        = (String) body.get("name");
        String       phoneNumber = (String) body.get("phoneNumber");
        Long         adminId     = Long.valueOf(body.get("paidToAdminId").toString());
        @SuppressWarnings("unchecked")
        List<String> months      = (List<String>) body.get("months");

        PaymentVerificationRequestDTO result = verificationService.submitMultiMonthCashRequest(
                owner.getId(), r.getId(), name, phoneNumber, months, adminId);
        return ResponseEntity.ok(ApiResponse.success(
                "Multi-month cash payment request submitted. Admin will verify shortly.", result));
    }

    @PostMapping(value = "/user/payment-verification/submit-multi-bank-transfer",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> submitMultiMonthBankTransferRequest(
            @RequestParam("name")                          String name,
            @RequestParam("phoneNumber")                   String phoneNumber,
            @RequestParam("months")                        List<String> months,
            @RequestParam("referenceId")                   String referenceId,
            @RequestParam(value = "bankName",  required = false) String bankName,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot,
            Authentication auth) throws IOException {

        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);

        PaymentVerificationRequestDTO result = verificationService.submitMultiMonthBankTransferRequest(
                owner.getId(), r.getId(), name, phoneNumber, months, referenceId, bankName, screenshot);
        return ResponseEntity.ok(ApiResponse.success(
                "Multi-month bank transfer details submitted. Admin will verify shortly.", result));
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
            @PathVariable Long id, Authentication auth) {
        viewerGuard.rejectViewer(auth);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment verified successfully", verificationService.verifyRequest(id, auth.getName())));
    }

    @PutMapping("/admin/payment-verification/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentVerificationRequestDTO>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        viewerGuard.rejectViewer(auth);
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Payment request rejected", verificationService.rejectRequest(id, reason)));
    }

}