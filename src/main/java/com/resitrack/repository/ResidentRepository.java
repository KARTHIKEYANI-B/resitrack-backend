package com.resitrack.repository;

import com.resitrack.entity.PropertyType;
import com.resitrack.entity.Resident;
import com.resitrack.entity.Resident.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResidentRepository extends JpaRepository<Resident, Long> {

    Optional<Resident> findByEmail(String email);
    Optional<Resident> findByRegisterNumber(String registerNumber);
    Optional<Resident> findByPhone(String phone);
    Optional<Resident> findByFamilyMemberId(Long familyMemberId);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByFlatNumber(String flatNumber);
    boolean existsByRegisterNumber(String registerNumber);
    boolean existsByFamilyMemberId(Long familyMemberId);


    @Query("SELECT COUNT(r) FROM Resident r WHERE r.residentRole = 'OWNER' AND r.password <> '__DELETED__'")
    long countOwners();

    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.registrationStatus = :status " +
           "AND r.password <> '__DELETED__'")
    long countOwnersByRegistrationStatus(@Param("status") RegistrationStatus status);

    long countByRegisteredTrue();
    long countByRegistrationStatus(RegistrationStatus status);
    long countByIsApprovedTrue();


    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.propertyType = :propertyType " +
           "AND r.password <> '__DELETED__'")
    long countOwnersByPropertyType(@Param("propertyType") PropertyType propertyType);

    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.propertyType = :propertyType " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__'")
    long countActiveOwnersByPropertyType(@Param("propertyType") PropertyType propertyType);

    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.propertyType = :propertyType " +
           "AND r.status = :status " +
           "AND r.password <> '__DELETED__'")
    long countByPropertyTypeAndStatus(@Param("propertyType") PropertyType propertyType,
                                       @Param("status") Resident.ResidentStatus status);


    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.propertyType = :propertyType " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__'")
    long countActiveNonDeletedByPropertyType(@Param("propertyType") PropertyType propertyType);

    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__'")
    long countAllActiveNonDeleted();

    @Query("SELECT r FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.propertyType = :propertyType " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__' " +
           "ORDER BY r.flatNumber ASC")
    List<Resident> findActiveNonDeletedByPropertyType(@Param("propertyType") PropertyType propertyType);

    @Query("SELECT r FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__' " +
           "ORDER BY r.flatNumber ASC")
    List<Resident> findAllActiveNonDeleted();


    @Query("SELECT r FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.password <> '__DELETED__' " +
           "ORDER BY r.createdAt DESC")
    List<Resident> findAllOwnersByOrderByCreatedAtDesc();

    @Query("SELECT r FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.isApproved = true " +
           "AND r.status = 'ACTIVE' " +
           "AND r.password <> '__DELETED__'")
    List<Resident> findAllActiveApprovedOwners();

    List<Resident> findByRegistrationStatusOrderByCreatedAtDesc(RegistrationStatus status);

    @Query("SELECT r FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.isApproved = true " +
           "AND r.status = 'ACTIVE' " +
           "AND r.password <> '__DELETED__'")
    List<Resident> findByIsApprovedTrueAndStatus(Resident.ResidentStatus status);

    List<Resident> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(r) FROM Resident r " +
           "WHERE r.residentRole = 'FAMILY_MEMBER' " +
           "AND r.status = 'ACTIVE' " +
           "AND r.password <> '__DELETED__'")
    long countActiveFamilyMemberLogins();

    @Query("SELECT COALESCE(SUM(r.familyMembers), 0) FROM Resident r " +
           "WHERE r.residentRole = 'OWNER' " +
           "AND r.status = 'ACTIVE' " +
           "AND r.isApproved = true " +
           "AND r.password <> '__DELETED__'")
    long sumRegisteredFamilyMembersCount();
}