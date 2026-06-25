package com.resitrack.service;

import com.resitrack.dto.BatchDueDTO;
import com.resitrack.dto.BatchPaymentSubmitRequest;
import com.resitrack.dto.PaidListEntryDTO;
import com.resitrack.entity.*;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * BatchPaymentService
 * ─────────────────────────────────────────────────────────────────────────
 * Owns all read/write logic for the `batch_payments` table — the per-batch
 * payment ledger that is completely independent of the regular monthly
 * `payments` table (Payment entity / PaymentService / PaymentVerificationService).
 *
 * Nothing in this class touches Maintenance, Payment, or any existing
 * dashboard/financial-summary query, so regular monthly maintenance
 * behaviour is unaffected by this feature.
 */
@Service
@RequiredArgsConstructor
public class BatchPaymentService {

    private final BatchPaymentRepository     batchPaymentRepo;
    private final MaintenanceBatchRepository batchRepo;
    private final ResidentRepository         residentRepo;
    private final AdminRepository            adminRepo;

    // ── Resident-facing: "Maintenance Batch Dues" list ─────────────────────

    /**
     * Returns every batch due that belongs to the given resident's PROPERTY.
     * `residentId` here is always the resolved property owner id (see
     * ResidentService.getEffectiveOwnerResident) so an Owner and any of
     * their Family Members see the exact same list for the same property.
     */
    public List<BatchDueDTO> getDuesForResident(Long ownerResidentId) {
        return batchPaymentRepo.findByResidentIdOrderByCreatedAtDesc(ownerResidentId)
                .stream()
                .map(this::toDueDTO)
                .collect(Collectors.toList());
    }

    private BatchDueDTO toDueDTO(BatchPayment bp) {
        MaintenanceBatch batch = bp.getBatch();
        return BatchDueDTO.builder()
                .batchPaymentId(bp.getId())
                .batchId(batch.getId())
                .batchTitle(batch.getTitle())
                .category(batch.getCategory())
                .description(batch.getDescription())
                .amount(bp.getAmount())
                .dueDate(batch.getDueDate())
                .status(bp.getStatus().name())
                .paymentMethod(bp.getPaymentMethod())
                .transactionId(bp.getTransactionId())
                .submittedDate(bp.getSubmittedDate())
                .verifiedDate(bp.getVerifiedDate())
                .rejectionReason(bp.getRejectionReason())
                .build();
    }

    // ── Resident-facing: submit a payment for a batch due ──────────────────

    @Transactional
    public BatchDueDTO submitPayment(Long ownerResidentId, Long payerResidentId,
                                      Long batchPaymentId, BatchPaymentSubmitRequest req) {

        BatchPayment bp = batchPaymentRepo.findById(batchPaymentId)
                .orElseThrow(() -> new CustomException("Batch due not found", HttpStatus.NOT_FOUND));

        // Ownership check: a resident may only pay a due that belongs to
        // their own property (the resolved owner id), never another flat's.
        if (!bp.getResident().getId().equals(ownerResidentId))
            throw new CustomException("This batch due does not belong to your property", HttpStatus.FORBIDDEN);

        if (bp.getStatus() == BatchPayment.BatchPaymentStatus.PAID)
            throw new CustomException("This batch due is already paid", HttpStatus.BAD_REQUEST);

        if (req.getPaymentMethod() == null || req.getPaymentMethod().isBlank())
            throw new CustomException("Payment method is required", HttpStatus.BAD_REQUEST);

        String method = req.getPaymentMethod().trim().toUpperCase();
        if (!List.of("UPI", "BANK_TRANSFER", "CASH", "GPAY").contains(method))
            throw new CustomException("Invalid payment method. Use: UPI, BANK_TRANSFER, CASH", HttpStatus.BAD_REQUEST);

        Resident payer = residentRepo.findById(payerResidentId)
                .orElseThrow(() -> new CustomException("Resident not found", HttpStatus.NOT_FOUND));

        bp.setPaymentMethod(method);
        bp.setTransactionId(req.getTransactionId() != null ? req.getTransactionId().trim() : null);
        bp.setSubmittedDate(LocalDate.now());
        bp.setPaidByResidentId(payer.getId());
        bp.setPaidByName(payer.getFullName());
        bp.setPaidByRole(payer.getResidentRole());
        bp.setStatus(BatchPayment.BatchPaymentStatus.PENDING_VERIFICATION);
        bp.setRejectionReason(null);

        batchPaymentRepo.save(bp);
        recalculateBatchCounts(bp.getBatch().getId());

        return toDueDTO(bp);
    }

    // ── Admin-facing: Paid List for a batch ─────────────────────────────────

    public List<PaidListEntryDTO> getPaidList(Long batchId) {
        if (!batchRepo.existsById(batchId))
            throw new CustomException("Batch not found", HttpStatus.NOT_FOUND);

        return batchPaymentRepo.findPaidListByBatchId(batchId)
                .stream()
                .map(this::toEntryDTO)
                .collect(Collectors.toList());
    }

    /** All payment rows (any status) for a batch — used for the admin detail/unpaid view. */
    public List<PaidListEntryDTO> getAllPaymentsForBatch(Long batchId) {
        if (!batchRepo.existsById(batchId))
            throw new CustomException("Batch not found", HttpStatus.NOT_FOUND);

        return batchPaymentRepo.findByBatchIdOrderByCreatedAtDesc(batchId)
                .stream()
                .map(this::toEntryDTO)
                .collect(Collectors.toList());
    }

    /**
     * All Maintenance Batch payments currently awaiting admin verification,
     * across every batch — feeds the Admin → Payment Verification screen.
     * Entirely separate query/table from the monthly
     * `payment_verification_requests` feed (PaymentVerificationService).
     */
    public List<PaidListEntryDTO> getPendingVerification() {
        return batchPaymentRepo.findAllPendingVerification()
                .stream()
                .map(this::toEntryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Shared row-builder so the Paid List, the full batch ledger, and the
     * Payment Verification feed all describe a BatchPayment identically —
     * including the Owner Name / Family Member Name split:
     *   - ownerName         = the property owner (BatchPayment.resident — always set)
     *   - familyMemberName  = set only when the payer was a Family Member,
     *                         i.e. paidByRole == FAMILY_MEMBER
     */
    private PaidListEntryDTO toEntryDTO(BatchPayment bp) {
        boolean paidByFamilyMember = bp.getPaidByRole() == Resident.ResidentRole.FAMILY_MEMBER;
        String ownerName = bp.getResident().getFullName();

        return PaidListEntryDTO.builder()
                .batchPaymentId(bp.getId())
                .batchId(bp.getBatch().getId())
                .batchTitle(bp.getBatch().getTitle())
                .residentName(ownerName)
                .ownerName(ownerName)
                .familyMemberName(paidByFamilyMember ? bp.getPaidByName() : null)
                .flatNumber(bp.getResident().getFlatNumber())
                .amount(bp.getAmount())
                .status(bp.getStatus().name())
                .paidDate(bp.getVerifiedDate())
                .submittedDate(bp.getSubmittedDate())
                .paidBy(bp.getPaidByName())
                .paidByRole(bp.getPaidByRole() != null ? bp.getPaidByRole().name() : null)
                .paymentMethod(bp.getPaymentMethod())
                .transactionId(bp.getTransactionId())
                .build();
    }

    // ── Admin-facing: verify / reject a submitted batch payment ────────────

    @Transactional
    public PaidListEntryDTO verifyPayment(Long batchPaymentId, String adminEmail) {
        BatchPayment bp = batchPaymentRepo.findById(batchPaymentId)
                .orElseThrow(() -> new CustomException("Batch payment not found", HttpStatus.NOT_FOUND));

        if (bp.getStatus() != BatchPayment.BatchPaymentStatus.PENDING_VERIFICATION)
            throw new CustomException(
                    "Only payments pending verification can be verified", HttpStatus.BAD_REQUEST);

        bp.setStatus(BatchPayment.BatchPaymentStatus.PAID);
        bp.setVerifiedDate(LocalDate.now());

        Optional<Admin> admin = adminEmail != null ? adminRepo.findByEmail(adminEmail) : Optional.empty();
        admin.ifPresent(a -> bp.setVerifiedByAdminId(a.getId()));

        batchPaymentRepo.save(bp);

        // ── Requirement 6: increment paid count, remove from unpaid count ──
        recalculateBatchCounts(bp.getBatch().getId());

        return toEntryDTO(bp);
    }

    @Transactional
    public PaidListEntryDTO rejectPayment(Long batchPaymentId, String reason) {
        BatchPayment bp = batchPaymentRepo.findById(batchPaymentId)
                .orElseThrow(() -> new CustomException("Batch payment not found", HttpStatus.NOT_FOUND));

        if (bp.getStatus() != BatchPayment.BatchPaymentStatus.PENDING_VERIFICATION)
            throw new CustomException(
                    "Only payments pending verification can be rejected", HttpStatus.BAD_REQUEST);

        bp.setStatus(BatchPayment.BatchPaymentStatus.UNPAID);
        bp.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Payment could not be verified");
        batchPaymentRepo.save(bp);

        recalculateBatchCounts(bp.getBatch().getId());

        return toEntryDTO(bp);
    }

    /**
     * Admin can also directly mark a due as paid (e.g. cash collected in
     * person, no resident submission needed) — same verification semantics,
     * just skips the PENDING_VERIFICATION intermediate state.
     */
    @Transactional
    public PaidListEntryDTO markPaidByAdmin(Long batchPaymentId, String adminEmail, String paymentMethod) {
        BatchPayment bp = batchPaymentRepo.findById(batchPaymentId)
                .orElseThrow(() -> new CustomException("Batch payment not found", HttpStatus.NOT_FOUND));

        if (bp.getStatus() == BatchPayment.BatchPaymentStatus.PAID)
            throw new CustomException("This batch due is already paid", HttpStatus.BAD_REQUEST);

        bp.setStatus(BatchPayment.BatchPaymentStatus.PAID);
        bp.setVerifiedDate(LocalDate.now());
        bp.setSubmittedDate(bp.getSubmittedDate() != null ? bp.getSubmittedDate() : LocalDate.now());
        if (paymentMethod != null && !paymentMethod.isBlank()) bp.setPaymentMethod(paymentMethod.toUpperCase());
        if (bp.getPaidByName() == null) {
            bp.setPaidByName(bp.getResident().getFullName());
            bp.setPaidByRole(bp.getResident().getResidentRole());
            bp.setPaidByResidentId(bp.getResident().getId());
        }

        Optional<Admin> admin = adminEmail != null ? adminRepo.findByEmail(adminEmail) : Optional.empty();
        admin.ifPresent(a -> bp.setVerifiedByAdminId(a.getId()));

        batchPaymentRepo.save(bp);
        recalculateBatchCounts(bp.getBatch().getId());

        return toEntryDTO(bp);
    }

    // ── Internal: recompute and persist this batch's own paid/unpaid counts ─
    //
    // This is the single place that writes MaintenanceBatch.paidCount /
    // unpaidCount, and it is always derived from batch_payments rows
    // filtered by batch_id — never from the calendar month, never from the
    // generic `payments` table. This is what makes the counts isolated
    // per-batch as required.
    @Transactional
    public void recalculateBatchCounts(Long batchId) {
        MaintenanceBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));

        long paid   = batchPaymentRepo.countPaidByBatchId(batchId);
        long unpaid = batchPaymentRepo.countUnpaidByBatchId(batchId);

        batch.setPaidCount((int) paid);
        batch.setUnpaidCount((int) unpaid);
        batchRepo.save(batch);
    }
}