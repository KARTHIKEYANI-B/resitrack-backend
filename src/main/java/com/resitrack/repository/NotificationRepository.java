package com.resitrack.repository;

import com.resitrack.entity.Notification;
import com.resitrack.entity.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n " +
           "WHERE n.recipientRole = 'USER' " +
           "AND ( n.targetResidentId = :residentId " +
           "      OR ( n.targetResidentId IS NULL " +
           "           AND ( n.targetPropertyType IS NULL " +
           "                 OR n.targetPropertyType = :propertyType ) ) ) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findAllForResident(
            @Param("residentId")   Long residentId,
            @Param("propertyType") PropertyType propertyType);

    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.recipientRole = 'USER' AND n.isRead = false " +
           "AND ( n.targetResidentId = :residentId " +
           "      OR ( n.targetResidentId IS NULL " +
           "           AND ( n.targetPropertyType IS NULL " +
           "                 OR n.targetPropertyType = :propertyType ) ) )")
    long countUnreadForResident(
            @Param("residentId")   Long residentId,
            @Param("propertyType") PropertyType propertyType);

    List<Notification> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.recipientRole = 'ADMIN' AND n.isRead = false")
    long countUnreadForAdmin();

    @Query("SELECT n FROM Notification n " +
           "WHERE n.recipientRole = 'ADMIN' " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findAllAdminNotifications();

    @Query("SELECT n FROM Notification n " +
           "WHERE n.recipientRole = 'ADMIN' " +
           "AND ( n.targetAdminId IS NULL " +
           "      OR n.targetAdminId = :adminId " +
           "      OR :isSuperAdmin = true ) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findAllAdminNotificationsForAdmin(
            @Param("adminId")      Long    adminId,
            @Param("isSuperAdmin") boolean isSuperAdmin);

    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.recipientRole = 'ADMIN' AND n.isRead = false " +
           "AND ( n.targetAdminId IS NULL " +
           "      OR n.targetAdminId = :adminId " +
           "      OR :isSuperAdmin = true )")
    long countUnreadForAdminId(
            @Param("adminId")      Long    adminId,
            @Param("isSuperAdmin") boolean isSuperAdmin);

    List<Notification> findByTargetResidentIdOrderByCreatedAtDesc(Long targetResidentId);

    List<Notification> findByTargetResidentIdIsNullOrderByCreatedAtDesc();

    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
           "WHERE n.targetResidentId = :residentId " +
           "AND n.type = :type " +
           "AND FUNCTION('DATE', n.createdAt) = :date")
    boolean existsByTargetResidentIdAndTypeAndCreatedAtDate(
            @Param("residentId") Long residentId,
            @Param("type") Notification.NotificationType type,
            @Param("date") LocalDate date);

    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
           "WHERE n.targetResidentId = :residentId " +
           "AND n.type = :type " +
           "AND n.title LIKE CONCAT('%', :titlePart, '%') " +
           "AND FUNCTION('DATE', n.createdAt) = :date")
    boolean existsByTargetResidentIdAndTypeAndTitleContainingAndCreatedAtDate(
            @Param("residentId") Long residentId,
            @Param("type") Notification.NotificationType type,
            @Param("titlePart") String titlePart,
            @Param("date") LocalDate date);

    // ── Security guard queries (NEW) ──────────────────────────────────────

    @Query("SELECT n FROM Notification n " +
           "WHERE n.recipientRole = 'SECURITY' " +
           "AND n.targetAdminId = :guardId " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findSecurityNotificationsForGuard(@Param("guardId") Long guardId);

    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.recipientRole = 'SECURITY' " +
           "AND n.targetAdminId = :guardId " +
           "AND n.isRead = false")
    long countUnreadSecurityNotifications(@Param("guardId") Long guardId);
}