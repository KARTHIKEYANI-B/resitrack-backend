package com.resitrack.repository;

import com.resitrack.entity.DoctorAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorAppointmentRepository extends JpaRepository<DoctorAppointment, Long> {

    List<DoctorAppointment> findByResidentIdAndActiveTrueOrderByAppointmentDateDesc(Long residentId);

    Optional<DoctorAppointment> findByIdAndResidentId(Long id, Long residentId);
}
