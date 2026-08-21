package com.resitrack.repository;

import com.resitrack.entity.InsuranceDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceDetailRepository extends JpaRepository<InsuranceDetail, Long> {

    List<InsuranceDetail> findByResidentIdAndActiveTrueOrderByCreatedAtDesc(Long residentId);

    Optional<InsuranceDetail> findByIdAndResidentId(Long id, Long residentId);
}
