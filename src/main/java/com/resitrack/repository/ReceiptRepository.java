package com.resitrack.repository;

import com.resitrack.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findByResidentIdOrderByGeneratedAtDesc(Long residentId);

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    Optional<Receipt> findByPaymentId(Long paymentId);

    List<Receipt> findAllByOrderByGeneratedAtDesc();
}
