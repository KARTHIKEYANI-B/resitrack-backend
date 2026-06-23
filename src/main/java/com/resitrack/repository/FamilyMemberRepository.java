package com.resitrack.repository;

import com.resitrack.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByResidentIdOrderByCreatedAtAsc(Long residentId);

    List<FamilyMember> findByResidentIdAndActiveTrueOrderByCreatedAtAsc(Long residentId);

    Optional<FamilyMember> findByUserId(Long userId);

    long countByResidentIdAndActiveTrue(Long residentId);

    long countByActiveTrue();

    long countByActiveTrueAndHasAppAccessTrue();

    @Query("SELECT COUNT(f) FROM FamilyMember f WHERE f.active = true AND f.age BETWEEN :low AND :high")
    long countByAgeBetween(@Param("low") int low, @Param("high") int high);

    @Query("SELECT COUNT(f) FROM FamilyMember f WHERE f.active = true AND f.age >= :low")
    long countByAgeGte(@Param("low") int low);

    @Query("SELECT COUNT(f) FROM FamilyMember f WHERE f.active = true AND f.age IS NULL")
    long countByAgeNull();

    @Query("SELECT COUNT(f) FROM FamilyMember f WHERE f.active = true AND f.age BETWEEN :minAge AND :maxAge")
    long countByAgeRange(@Param("minAge") int minAge, @Param("maxAge") int maxAge);

    @Query("SELECT f FROM FamilyMember f WHERE f.active = true AND f.age BETWEEN :minAge AND :maxAge ORDER BY f.age ASC, f.name ASC")
    List<FamilyMember> findByAgeRange(@Param("minAge") int minAge, @Param("maxAge") int maxAge);

    List<FamilyMember> findByResidentIdAndActiveTrue(Long residentId);

    // ── Family-member personal contact lookups (used for login fallback) ──
    // These let us find a Family Member by their personal contact email/phone
    // (stored in family_members.email / family_members.phone) when app access
    // has been granted — so we can resolve the linked Resident login account
    // (via userId) and allow login with the personal email, not just the
    // separate login email stored in residents.email.

    Optional<FamilyMember> findByEmailAndHasAppAccessTrue(String email);

    Optional<FamilyMember> findByPhoneAndHasAppAccessTrue(String phone);
}