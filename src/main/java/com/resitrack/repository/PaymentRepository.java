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

    /**
     * Property-level paid sum for a given month.
     *
     * Sums PAID payments for the owner AND all Family Members whose
     * resident row has owner_resident_id = ownerResidentId.
     *
     * Written as native SQL (nativeQuery = true) because Hibernate 6
     * (Spring Boot 3.x) does NOT support SpEL #{T(...)} inside JPQL
     * @Query — that syntax only works with nativeQuery = true.
     * Using plain SQL 'PAID' is safe because paymentStatus is stored as
     * a VARCHAR via @Enumerated(EnumType.STRING).
     *
     * Returns 0.0 (never NULL) via COALESCE.
     *
     * @param ownerResidentId  The OWNER's resident_id (never a FM id)
     * @param paymentMonth     e.g. "2025-06"
     */
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

    /**
     * Original single-resident paid sum — used only for user-facing queries
     * (payment history, user dashboard) where only one resident's own
     * payments are needed.
     */
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

    /**
     * Same intent as findByStatusAndYearRange, but the range bound is the
     * BILLING month (p.paymentMonth, e.g. "2026-05") rather than the
     * calendar month a payment happened to be collected/verified in
     * (p.paymentDate). Used by Resident Paid/Unpaid Detail so that two
     * installments paid for the SAME bill always land in the same matrix
     * cell even if they were collected on different calendar dates —
     * p.paymentMonth never changes between installments of one bill, while
     * p.paymentDate can (e.g. a partial payment collected late next month).
     *
     * Safe to compare as a plain string BETWEEN: paymentMonth is always
     * zero-padded "YYYY-MM", so lexicographic and chronological ordering
     * agree (e.g. "2026-01" < "2026-02" < ... < "2026-12" < "2027-01").
     */
    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = :status " +
           "AND p.paymentMonth BETWEEN :startMonth AND :endMonth")
    List<Payment> findByStatusAndPaymentMonthRange(
            @Param("status") Payment.PaymentStatus status,
            @Param("startMonth") String startMonth,
            @Param("endMonth") String endMonth);

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

    /**
     * Task 1 — Duplicate Payment Cleanup.
     *
     * Finds every (resident_id, payment_month) pair that has MORE THAN ONE
     * PAID payment row — i.e. an owner who has been charged/credited twice
     * (or more) for the same maintenance month. This is the root cause of
     * Admin Dashboard / Maintenance Summary / Paid-Unpaid Details (which all
     * sum via sumPaidAmountByPropertyAndPaymentMonth, so duplicates simply
     * add into the same total they already show) silently agreeing with
     * each other while still being inflated by genuine duplicate rows.
     *
     * Returns one row per duplicated (resident, paymentMonth) group with the
     * group's row count and summed amount, so the admin can see exactly
     * which owner/month combinations need manual review before deleting the
     * extra row(s) via DELETE /admin/payments/{id}.
     */
    @Query("SELECT p.resident.id AS residentId, p.paymentMonth AS paymentMonth, " +
           "COUNT(p) AS duplicateCount, SUM(p.amount) AS totalAmount " +
           "FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' AND p.paymentMonth IS NOT NULL " +
           "GROUP BY p.resident.id, p.paymentMonth " +
           "HAVING COUNT(p) > 1")
    List<DuplicatePaymentGroup> findDuplicatePaidGroups();

    interface DuplicatePaymentGroup {
        Long getResidentId();
        String getPaymentMonth();
        Long getDuplicateCount();
        java.math.BigDecimal getTotalAmount();
    }

    /**
     * All PAID payments for one resident + billing month, oldest first — used
     * to list the individual duplicate rows (with id, amount, date, method)
     * once findDuplicatePaidGroups() has identified an affected group.
     */
    @Query("SELECT p FROM Payment p WHERE p.resident.id = :residentId " +
           "AND p.paymentMonth = :paymentMonth AND p.paymentStatus = 'PAID' " +
           "ORDER BY p.createdAt ASC")
    List<Payment> findPaidByResidentIdAndPaymentMonth(
            @Param("residentId") Long residentId,
            @Param("paymentMonth") String paymentMonth);
}