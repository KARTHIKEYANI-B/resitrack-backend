package com.resitrack.repository;

import com.resitrack.entity.Maintenance;
import com.resitrack.entity.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Active (active = true) rows for a given property type, newest first.
    // Used by MaintenanceService to resolve the single current FLAT/VILLA
    // rate config and to find sibling rows to deactivate when a new one of
    // the same property type is created/updated.
    // Explicit @Query because "findActiveBy..." is not itself a parseable
    // Spring Data derived-query clause (there's no field literally named
    // "Active" connected via And/Or to "PropertyType") — the @Query pins
    // down the exact intended filter: active = true AND propertyType = :propertyType.
    @Query("SELECT m FROM Maintenance m WHERE m.active = true AND m.propertyType = :propertyType ORDER BY m.createdAt DESC")
    List<Maintenance> findActiveByPropertyTypeOrderByCreatedAtDesc(@Param("propertyType") PropertyType propertyType);
}