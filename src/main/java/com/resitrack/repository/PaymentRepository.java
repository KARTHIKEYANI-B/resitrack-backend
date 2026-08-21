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
     * Property-level paid sum for a given BILLING month.
     *
     * Sums PAID payments for the owner AND all Family Members whose
     * resident row has owner_resident_id = ownerResidentId.
     *
     * ALLOCATION-AWARE: a regular (single-month) Payment row credits its
     * own payment_month directly, exactly as before. A multi-month admin
     * "Record Payment" entry (is_multi_month = 1) instead keeps its own
     * (payment_month, amount) as the UNDIVIDED total — used only by
     * whole-society, billing-month-keyed reports (Financial Summary,
     * Analytics, Payment Management stats) which intentionally still show
     * the full amount in one bucket — and contributes to THIS query only
     * through its payment_month_allocations child rows, each carrying its
     * own month's share. This is what lets Maintenance Summary, Paid/Unpaid
     * Details and Pending Dues (all powered by this one query) correctly
     * mark each selected month as paid for its own amount, while every
     * pre-existing single-month payment (no allocation rows) behaves
     * identically to before — the second UNION branch simply contributes
     * nothing for those.
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
           "SELECT COALESCE(SUM(amt), 0) FROM (" +
           "  SELECT p.amount AS amt " +
           "  FROM payments p " +
           "  INNER JOIN residents r ON r.id = p.resident_id " +
           "  WHERE p.payment_status = 'PAID' " +
           "  AND   p.payment_month  = :paymentMonth " +
           "  AND  (p.is_multi_month = 0 OR p.is_multi_month IS NULL) " +
           "  AND  (p.resident_id = :ownerResidentId " +
           "        OR r.owner_resident_id = :ownerResidentId) " +
           "  UNION ALL " +
           "  SELECT pma.amount AS amt " +
           "  FROM payment_month_allocations pma " +
           "  INNER JOIN payments p2 ON p2.id = pma.payment_id " +
           "  INNER JOIN residents r2 ON r2.id = p2.resident_id " +
           "  WHERE p2.payment_status = 'PAID' " +
           "  AND   pma.payment_month = :paymentMonth " +
           "  AND  (p2.resident_id = :ownerResidentId " +
           "        OR r2.owner_resident_id = :ownerResidentId) " +
           ") combined",
           nativeQuery = true)
    Double sumPaidAmountByPropertyAndPaymentMonth(
            @Param("ownerResidentId") Long ownerResidentId,
            @Param("paymentMonth")    String paymentMonth);

    /**
     * Batched version of {@link #sumPaidAmountByPropertyAndPaymentMonth} —
     * computes the property-level (owner + linked Family Members) PAID sum
     * for EVERY owner in a single query instead of one query per owner.
     *
     * Added to fix an N+1 query pattern: MaintenanceService.buildOwnerDTOs()
     * previously called sumPaidAmountByPropertyAndPaymentMonth once per
     * resident, and the Admin Dashboard called that (via
     * getOwnerMaintenanceList) twice per load (current + previous month) —
     * for M residents that's up to 2×M sequential round trips on every
     * dashboard load, which is what made it slow to load as the resident
     * count grew. This is the same UNION of direct + allocation-row
     * payments as the per-owner query, just grouped by owner instead of
     * filtered to one.
     *
     * Returns one row per owner who has at least one matching payment this
     * month: [0] = owner's resident_id (Number), [1] = paid amount
     * (Number). Owners with no payment this month simply don't appear —
     * callers must default missing ids to zero.
     */
    @Query(value =
           "SELECT COALESCE(r.owner_resident_id, r.id) AS owner_id, COALESCE(SUM(combined.amt), 0) AS total " +
           "FROM (" +
           "  SELECT p.resident_id AS resident_id, p.amount AS amt " +
           "  FROM payments p " +
           "  WHERE p.payment_status = 'PAID' " +
           "  AND   p.payment_month  = :paymentMonth " +
           "  AND  (p.is_multi_month = 0 OR p.is_multi_month IS NULL) " +
           "  UNION ALL " +
           "  SELECT p2.resident_id AS resident_id, pma.amount AS amt " +
           "  FROM payment_month_allocations pma " +
           "  INNER JOIN payments p2 ON p2.id = pma.payment_id " +
           "  WHERE p2.payment_status = 'PAID' " +
           "  AND   pma.payment_month = :paymentMonth " +
           ") combined " +
           "INNER JOIN residents r ON r.id = combined.resident_id " +
           "GROUP BY COALESCE(r.owner_resident_id, r.id)",
           nativeQuery = true)
    List<Object[]> sumPaidAmountGroupedByOwnerForPaymentMonth(@Param("paymentMonth") String paymentMonth);

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

    // Sibling rows created in the same multi-month "Add Payment" submission
    // (see Payment.paymentBatchId) — used to build a consolidated receipt
    // showing every month/amount in that one payment instead of just one.
    List<Payment> findByPaymentBatchIdOrderByPaymentMonthAsc(String paymentBatchId);

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