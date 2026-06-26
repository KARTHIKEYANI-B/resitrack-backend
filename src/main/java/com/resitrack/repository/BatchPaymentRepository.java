package com.resitrack.repository;

import com.resitrack.entity.BatchPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchPaymentRepository extends JpaRepository<BatchPayment, Long> {

    // ── Scoped strictly to one batch — this is the fix for the original bug ──

    List<BatchPayment> findByBatchIdOrderByCreatedAtDesc(Long batchId);

    @Query("SELECT COUNT(bp) FROM BatchPayment bp WHERE bp.batch.id = :batchId AND bp.status = 'PAID'")
    long countPaidByBatchId(@Param("batchId") Long batchId);

    @Query("SELECT COUNT(bp) FROM BatchPayment bp WHERE bp.batch.id = :batchId AND bp.status <> 'PAID'")
    long countUnpaidByBatchId(@Param("batchId") Long batchId);

    @Query("SELECT bp FROM BatchPayment bp WHERE bp.batch.id = :batchId AND bp.status = 'PAID' " +
           "ORDER BY bp.verifiedDate DESC")
    List<BatchPayment> findPaidListByBatchId(@Param("batchId") Long batchId);

    Optional<BatchPayment> findByBatchIdAndResidentId(Long batchId, Long residentId);

    boolean existsByBatchIdAndResidentId(Long batchId, Long residentId);

    // ── Admin → Payment Verification feed ───────────────────────────────────
    // All batch payments awaiting verification, across every batch — this is
    // what surfaces resident-submitted Maintenance Batch payments in the
    // Admin → Payment Verification screen (kept in its own dedicated table,
    // never mixed with the monthly `payments`/`payment_verification_requests`
    // tables).
    @Query("SELECT bp FROM BatchPayment bp WHERE bp.status = 'PENDING_VERIFICATION' " +
           "ORDER BY bp.submittedDate DESC, bp.createdAt DESC")
    List<BatchPayment> findAllPendingVerification();

    // ── Resident-facing (Owner/Family Member dashboard) ─────────────────────

    @Query("SELECT bp FROM BatchPayment bp WHERE bp.resident.id = :residentId " +
           "ORDER BY bp.createdAt DESC")
    List<BatchPayment> findByResidentIdOrderByCreatedAtDesc(@Param("residentId") Long residentId);

    @Modifying
    @Query("DELETE FROM BatchPayment bp WHERE bp.batch.id = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    // ── Admin → Payment Management unified transaction ledger ──────────────
    // All PAID batch payments across every batch, newest verified first.
    // Purely additive read query — does not affect the per-batch paid/unpaid
    // counts, Payment Verification, or any other existing batch-payment flow.
    @Query("SELECT bp FROM BatchPayment bp WHERE bp.status = 'PAID' " +
           "ORDER BY bp.verifiedDate DESC, bp.createdAt DESC")
    List<BatchPayment> findAllPaidOrderByVerifiedDateDesc();
}