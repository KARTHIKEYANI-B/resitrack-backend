package com.resitrack.repository;

import com.resitrack.entity.PaymentVerificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentVerificationRequestRepository
        extends JpaRepository<PaymentVerificationRequest, Long> {

    List<PaymentVerificationRequest> findAllByOrderByCreatedAtDesc();

    List<PaymentVerificationRequest> findByResidentIdOrderByCreatedAtDesc(Long residentId);

    List<PaymentVerificationRequest> findByStatus(PaymentVerificationRequest.RequestStatus status);

    long countByStatus(PaymentVerificationRequest.RequestStatus status);

    @Query("SELECT COUNT(r) > 0 FROM PaymentVerificationRequest r " +
           "WHERE r.resident.id = :residentId " +
           "AND r.paymentMonth  = :paymentMonth " +
           "AND r.status        = 'PENDING'")
    boolean existsPendingByResidentIdAndPaymentMonth(
            @Param("residentId")   Long   residentId,
            @Param("paymentMonth") String paymentMonth);

    /**
     * Find all PENDING verification requests for a resident, ordered newest first.
     * Used to show the PENDING_VERIFICATION status on the Current Maintenance page.
     */
    @Query("SELECT r FROM PaymentVerificationRequest r " +
           "WHERE r.resident.id = :residentId " +
           "AND r.status        = 'PENDING' " +
           "ORDER BY r.createdAt DESC")
    List<PaymentVerificationRequest> findPendingByResidentId(
            @Param("residentId") Long residentId);
}