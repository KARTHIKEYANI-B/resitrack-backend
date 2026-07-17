package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.ReceiptResponseDTO;
import com.resitrack.entity.Resident;
import com.resitrack.service.ReceiptService;
import com.resitrack.service.ResidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService  receiptService;
    private final ResidentService residentService;

    @GetMapping("/admin/receipts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReceiptResponseDTO>> getAllReceipts() {
        return ResponseEntity.ok(receiptService.getAllReceipts());
    }

    @GetMapping("/admin/receipts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReceiptResponseDTO>> getReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(receiptService.getById(id)));
    }

    @GetMapping("/admin/receipts/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminDownloadReceipt(@PathVariable Long id) {
        ReceiptResponseDTO receipt = receiptService.getById(id);
        byte[] pdf = receiptService.generateReceiptPdf(receipt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"receipt-" + receipt.getReceiptNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/user/receipts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ReceiptResponseDTO>> getMyReceipts(Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        // Family members see all receipts for their linked flat/property
        return ResponseEntity.ok(receiptService.getResidentReceipts(owner.getId()));
    }

    @GetMapping("/user/receipts/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReceiptResponseDTO>> getUserReceiptById(
            @PathVariable Long id, Authentication auth) {
        ReceiptResponseDTO receipt = receiptService.getById(id);
        assertOwnedByCaller(receipt, auth);
        return ResponseEntity.ok(ApiResponse.success(receipt));
    }

    @GetMapping("/user/receipts/{id}/download")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> userDownloadReceipt(@PathVariable Long id, Authentication auth) {
        ReceiptResponseDTO receipt = receiptService.getById(id);
        assertOwnedByCaller(receipt, auth);
        byte[] pdf = receiptService.generateReceiptPdf(receipt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"receipt-" + receipt.getReceiptNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }


    private void assertOwnedByCaller(ReceiptResponseDTO receipt, Authentication auth) {
        Resident r     = residentService.getByEmail(auth.getName());
        Resident owner = residentService.getEffectiveOwnerResident(r);
        if (receipt.getResidentId() == null || !receipt.getResidentId().equals(owner.getId())) {
            throw new com.resitrack.exception.CustomException(
                    "Receipt not found", org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }
}