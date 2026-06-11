package com.resitrack.repository;

import com.resitrack.entity.MaintenanceBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceBatchRepository extends JpaRepository<MaintenanceBatch, Long> {

    List<MaintenanceBatch> findAllByOrderByCreatedAtDesc();

    List<MaintenanceBatch> findByStatus(MaintenanceBatch.BatchStatus status);

    List<MaintenanceBatch> findByCategory(String category);
}