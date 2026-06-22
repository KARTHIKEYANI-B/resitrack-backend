package com.resitrack.repository;

import com.resitrack.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByResidentIdAndActiveTrueOrderByCreatedAtAsc(Long residentId);

    List<Vehicle> findByResidentIdOrderByCreatedAtAsc(Long residentId);

    Optional<Vehicle> findByIdAndResidentId(Long id, Long residentId);

    long countByResidentIdAndActiveTrue(Long residentId);
}
