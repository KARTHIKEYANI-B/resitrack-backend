package com.resitrack.repository;

import com.resitrack.entity.Admin;
import com.resitrack.entity.AdminAssignment;
import com.resitrack.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminAssignmentRepository extends JpaRepository<AdminAssignment, Long> {

    Optional<AdminAssignment> findByPositionAndActiveTrue(Member.Position position);

    List<AdminAssignment> findByResidentIdAndActiveTrue(Long residentId);

    List<AdminAssignment> findByResidentIdOrderByStartDateDesc(Long residentId);

    List<AdminAssignment> findByPositionOrderByStartDateDesc(Member.Position position);

    List<AdminAssignment> findByActiveTrueOrderByPositionAsc();

    Optional<AdminAssignment> findByAdminIdAndActiveTrue(Long adminId);

    boolean existsByResidentIdAndActiveTrue(Long residentId);

    @Query("SELECT a FROM AdminAssignment a WHERE a.resident.id = :residentId AND a.position = :position AND a.active = true")
    Optional<AdminAssignment> findActiveByResidentAndPosition(
            @Param("residentId") Long residentId,
            @Param("position")   Member.Position position);

    // Used by DataInitializer.purgeLegacyAccounts() to remove historical assignment
    // rows before deleting the old apartment.com admin accounts.
    List<AdminAssignment> findByAdmin(Admin admin);
}