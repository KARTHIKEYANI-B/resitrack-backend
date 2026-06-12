package com.resitrack.repository;

import com.resitrack.entity.SecurityGuard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityGuardRepository extends JpaRepository<SecurityGuard, Long> {

    Optional<SecurityGuard> findByEmail(String email);

    Optional<SecurityGuard> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<SecurityGuard> findAllByOrderByCreatedAtDesc();
}