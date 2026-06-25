package com.resitrack.controller;

import com.resitrack.dto.ApiResponse;
import com.resitrack.dto.PaidListEntryDTO;
import com.resitrack.service.BatchPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin → Maintenance → Maintenance Batch
 * ─────────────────────────────────────────────────────────────────────────
 * New, self-contained endpoints for the per-batch payment ledger
 * (BatchPayment), kept entirely separate from MaintenanceController /
 * PaymentController / PaymentVerificationController so none of the
 * existing monthly-maintenance, dashboard, or financial-summary endpoints
 * are touched.
 */
@RestController
@RequestMapping("/admin/maintenance/batch-payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BatchPaymentAdminController {

    private final BatchPaymentService batchPaymentService;

    /** Requirement 7: "Paid List" — Resident Name, Flat/Villa Number, Paid Date, Paid By. */
    @GetMapping("/batch/{batchId}/paid-list")
    public ResponseEntity<ApiResponse<List<PaidListEntryDTO>>> getPaidList(@PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(batchPaymentService.getPaidList(batchId)));
    }

    /** Full ledger (any status) for a batch — used for the unpaid/pending detail view. */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<List<PaidListEntryDTO>>> getAllForBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(batchPaymentService.getAllPaymentsForBatch(batchId)));
    }

    /**
     * Feeds Admin → Payment Verification: every Maintenance Batch payment
     * currently PENDING_VERIFICATION, across all batches. Read by the
     * frontend Payment Verification screen and merged client-side with the
     * (separate) monthly maintenance verification feed — the two are never
     * combined on the backend, and this query only ever touches
     * `batch_payments`.
     */
    @GetMapping("/pending-verification")
    public ResponseEntity<ApiResponse<List<PaidListEntryDTO>>> getPendingVerification() {
        return ResponseEntity.ok(ApiResponse.success(batchPaymentService.getPendingVerification()));
    }

    /** Requirement 5/6: Admin verifies a resident-submitted batch payment. */
    @PutMapping("/{batchPaymentId}/verify")
    public ResponseEntity<ApiResponse<PaidListEntryDTO>> verify(
            @PathVariable Long batchPaymentId, Authentication auth) {
        PaidListEntryDTO result = batchPaymentService.verifyPayment(batchPaymentId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Batch payment verified", result));
    }

    @PutMapping("/{batchPaymentId}/reject")
    public ResponseEntity<ApiResponse<PaidListEntryDTO>> reject(
            @PathVariable Long batchPaymentId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        PaidListEntryDTO result = batchPaymentService.rejectPayment(batchPaymentId, reason);
        return ResponseEntity.ok(ApiResponse.success("Batch payment rejected", result));
    }

    /** Admin records a payment directly (e.g. cash collected in person). */
    @PutMapping("/{batchPaymentId}/mark-paid")
    public ResponseEntity<ApiResponse<PaidListEntryDTO>> markPaid(
            @PathVariable Long batchPaymentId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String method = body != null ? body.get("paymentMethod") : null;
        PaidListEntryDTO result = batchPaymentService.markPaidByAdmin(batchPaymentId, auth.getName(), method);
        return ResponseEntity.ok(ApiResponse.success("Marked as paid", result));
    }
}