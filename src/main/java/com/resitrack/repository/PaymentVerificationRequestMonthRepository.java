package com.resitrack.repository;

import com.resitrack.entity.PaymentVerificationRequestMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentVerificationRequestMonthRepository
        extends JpaRepository<PaymentVerificationRequestMonth, Long> {

    List<PaymentVerificationRequestMonth> findByRequestId(Long requestId);
}
