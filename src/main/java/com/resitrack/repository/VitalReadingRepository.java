package com.resitrack.repository;

import com.resitrack.entity.VitalReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VitalReadingRepository extends JpaRepository<VitalReading, Long> {

    List<VitalReading> findByResidentIdAndReadingTypeAndActiveTrueOrderByReadingDateDescReadingTimeDesc(
            Long residentId, String readingType);

    Optional<VitalReading> findByIdAndResidentId(Long id, Long residentId);
}
