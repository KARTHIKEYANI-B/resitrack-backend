package com.resitrack.repository;

import com.resitrack.entity.LicenseDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseDetailRepository extends JpaRepository<LicenseDetail, Long> {

    List<LicenseDetail> findByResidentIdAndActiveTrueOrderByCreatedAtDesc(Long residentId);

    Optional<LicenseDetail> findByIdAndResidentId(Long id, Long residentId);
}
