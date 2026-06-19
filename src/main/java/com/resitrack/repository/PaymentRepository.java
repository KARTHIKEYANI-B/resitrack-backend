package com.resitrack.repository;

import com.resitrack.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByResidentId(Long residentId);
    List<Payment> findByPaymentStatus(Payment.PaymentStatus status);
    List<Payment> findAllByOrderByCreatedAtDesc();

    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = :status " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    List<Payment> findByYearAndMonthAndStatus(
            @Param("year") int year,
            @Param("month") int month,
            @Param("status") Payment.PaymentStatus status);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    Double sumPaidAmountByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    Long countPaidByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND p.paymentMonth = :paymentMonth")
    Long countPaidByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND p.paymentMonth = :paymentMonth")
    Long countDistinctResidentsPaidByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND p.paymentMonth = :paymentMonth")
    Double sumPaidAmountByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) IN ('UPI', 'BANK_TRANSFER') " +
           "AND p.paymentMonth = :paymentMonth")
    Double sumBankPaidByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) = 'CASH' " +
           "AND p.paymentMonth = :paymentMonth")
    Double sumCashPaidByPaymentMonth(@Param("paymentMonth") String paymentMonth);


    @Query(value =
           "SELECT COALESCE(SUM(p.amount), 0) " +
           "FROM payments p " +
           "INNER JOIN residents r ON r.id = p.resident_id " +
           "WHERE p.payment_status = 'PAID' " +
           "AND   p.payment_month  = :paymentMonth " +
           "AND  (p.resident_id = :ownerResidentId " +
           "      OR r.owner_resident_id = :ownerResidentId)",
           nativeQuery = true)
    Double sumPaidAmountByPropertyAndPaymentMonth(
            @Param("ownerResidentId") Long ownerResidentId,
                @Param("paymentMonth")    String paymentMonth);
                
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.resident.id = :residentId " +
           "AND p.paymentStatus = 'PAID' " +
           "AND p.paymentMonth = :paymentMonth")
    Double sumPaidAmountByResidentAndPaymentMonth(
            @Param("residentId") Long residentId,
            @Param("paymentMonth") String paymentMonth);

    @Query("SELECT p FROM Payment p WHERE p.resident.id = :residentId " +
           "AND p.paymentMonth = :paymentMonth ORDER BY p.createdAt ASC")
    List<Payment> findByResidentIdAndPaymentMonth(
            @Param("residentId") Long residentId,
            @Param("paymentMonth") String paymentMonth);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) <> 'CASH'")
    Double sumAllTimeBankCollected();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) = 'CASH'")
    Double sumAllTimeCashCollected();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) <> 'CASH' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    Double sumBankCollectedByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND UPPER(p.paymentMethod) = 'CASH' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    Double sumCashCollectedByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'PAID'")
    Double sumAllPaidAmount();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year")
    Double sumPaidAmountByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) BETWEEN 1 AND :endMonth")
    Double sumCollectedByYearUpToMonth(@Param("year") int year, @Param("endMonth") int endMonth);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND p.paymentMonth = :paymentMonth")
    Long countDistinctFullyPaidResidentsByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = :status " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) BETWEEN :startMonth AND :endMonth")
    List<Payment> findByStatusAndYearRange(
            @Param("status") Payment.PaymentStatus status,
            @Param("year") int year,
            @Param("startMonth") int startMonth,
            @Param("endMonth") int endMonth);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) BETWEEN :startMonth AND :endMonth")
    Double sumPaidAmountByYearRange(
            @Param("year") int year,
            @Param("startMonth") int startMonth,
            @Param("endMonth") int endMonth);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.resident.id = :residentId " +
           "AND p.paymentStatus = 'PAID' AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) BETWEEN :startMonth AND :endMonth")
    Double sumPaidByResidentAndYearRange(
            @Param("residentId") Long residentId,
            @Param("year") int year,
            @Param("startMonth") int startMonth,
            @Param("endMonth") int endMonth);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p " +
           "WHERE p.paymentStatus = 'PENDING_VERIFICATION'")
    long countPendingVerification();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentStatus = 'PENDING' " +
           "AND p.paymentMonth = :paymentMonth")
    long countPendingByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    boolean existsByResidentIdAndPaymentMonth(Long residentId, String paymentMonth);

    boolean existsByResidentIdAndPaymentMonthAndPaymentStatus(
            Long residentId, String paymentMonth, Payment.PaymentStatus status);

    List<Payment> findByResidentIdOrderByCreatedAtDesc(Long residentId);

    boolean existsByResidentIdAndPaymentStatus(Long residentId, Payment.PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = 'PENDING' " +
           "AND p.paymentMonth = :paymentMonth ORDER BY p.createdAt DESC")
    List<Payment> findPendingByPaymentMonth(@Param("paymentMonth") String paymentMonth);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentStatus = :status AND p.paymentMonth = :month")
    long countByPaymentStatusForMonth(
            @Param("status") Payment.PaymentStatus status,
            @Param("month") String month);

    @Query("SELECT p FROM Payment p WHERE p.paymentStatus IN ('PENDING', 'OVERDUE') " +
           "ORDER BY p.paymentMonth ASC")
    List<Payment> findAllPendingOrOverdue();

    @Query("SELECT p FROM Payment p WHERE p.resident.id = :residentId " +
           "AND p.paymentStatus IN ('PENDING', 'OVERDUE') ORDER BY p.paymentMonth ASC")
    List<Payment> findPendingOrOverdueByResident(@Param("residentId") Long residentId);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year AND MONTH(p.paymentDate) = :month")
    Long countDistinctPaidResidentsByYearAndMonth(
            @Param("year") int year,
            @Param("month") int month);

    @Query("SELECT COUNT(DISTINCT p.resident.id) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) = :month")
    Long countActiveNonDeletedByPropertyType(
            @Param("year") int year,
            @Param("month") int month);
}