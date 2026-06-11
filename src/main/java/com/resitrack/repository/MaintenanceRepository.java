package com.resitrack.repository;

import com.resitrack.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
     List<Maintenance> findByMaintenanceType(String maintenanceType);

    Optional<Maintenance> findFirstByMaintenanceTypeAndActiveTrue(
            String maintenanceType
    );

    Optional<Maintenance> findFirstByActiveOrderByCreatedAtDesc(boolean active);

    List<Maintenance> findByActiveOrderByCreatedAtDesc(boolean active);
}
