package com.resitrack.repository;

import com.resitrack.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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
}