package com.resitrack.repository;

import com.resitrack.entity.TaxCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxCategoryRepository extends JpaRepository<TaxCategory, Long> {

    List<TaxCategory> findByResidentIdAndActiveTrueOrderByDueDateAsc(Long residentId);

    long countByResidentIdAndActiveTrue(Long residentId);
}
