package com.resitrack.repository;

import com.resitrack.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Admin> findByPhone(String phone);

    long countBySuperAdminTrue();

    Optional<Admin> findByResidentId(Long residentId);
    Optional<Admin> findFirstBySuperAdminTrue();
    boolean existsBySuperAdminTrue();

    long countByResidentIdIsNull();

    // Fallback signature source for receipts generated before any admin had
    // uploaded a signature (see ReceiptService.resolveDisplaySignatureUrl) —
    // any admin or super admin account with a signature set is eligible,
    // not just whichever one happened to process that specific payment.
    List<Admin> findBySignatureUrlIsNotNull();
}