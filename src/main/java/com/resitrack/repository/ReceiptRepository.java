package com.resitrack.repository;

import com.resitrack.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findByResidentIdOrderByGeneratedAtDesc(Long residentId);

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    Optional<Receipt> findByPaymentId(Long paymentId);

    List<Receipt> findAllByOrderByGeneratedAtDesc();

    // Receipts created before Receipt.propertyType existed — one-time
    // backfill target, see DataInitializer.backfillReceiptPropertyTypes().
    // JOIN FETCH loads `resident` eagerly in this same query, since that
    // backfill runs from a CommandLineRunner with no open Hibernate session
    // by the time it would otherwise touch the lazy `resident` proxy.
    @Query("SELECT r FROM Receipt r JOIN FETCH r.resident WHERE r.propertyType IS NULL")
    List<Receipt> findByPropertyTypeIsNullFetchResident();
}
