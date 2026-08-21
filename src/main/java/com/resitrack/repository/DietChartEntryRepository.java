package com.resitrack.repository;

import com.resitrack.entity.DietChartEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DietChartEntryRepository extends JpaRepository<DietChartEntry, Long> {

    List<DietChartEntry> findByResidentIdAndActiveTrueOrderByCreatedAtDesc(Long residentId);

    Optional<DietChartEntry> findByIdAndResidentId(Long id, Long residentId);
}
