package com.resitrack.repository;

import com.resitrack.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByCategory(String category);

    @Query("SELECT e FROM Expense e WHERE YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month ORDER BY e.expenseDate")
    List<Expense> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month")
    Double sumByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE YEAR(e.expenseDate) = :year")
    Double sumByYear(@Param("year") int year);

    @Query("SELECT e FROM Expense e " +
           "WHERE YEAR(e.expenseDate) = :year " +
           "AND MONTH(e.expenseDate) BETWEEN :startMonth AND :endMonth " +
           "ORDER BY e.expenseDate")
    List<Expense> findByYearRange(
            @Param("year") int year,
            @Param("startMonth") int startMonth,
            @Param("endMonth") int endMonth);

    @Query("SELECT SUM(e.amount) FROM Expense e " +
           "WHERE YEAR(e.expenseDate) = :year " +
           "AND MONTH(e.expenseDate) BETWEEN :startMonth AND :endMonth")
    Double sumByYearRange(
            @Param("year") int year,
            @Param("startMonth") int startMonth,
            @Param("endMonth") int endMonth);

    @Query("SELECT SUM(e.amount) FROM Expense e " +
           "WHERE (YEAR(e.expenseDate) < :year " +
           "       OR (YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) < :month))")
    Double sumByYearBeforeMonth(@Param("year") int year, @Param("month") int month);


    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.paymentMethod IS NOT NULL " +
           "AND LOWER(e.paymentMethod) <> 'cash'")
    Double sumAllTimeBankExpense();

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE (e.paymentMethod IS NULL OR LOWER(e.paymentMethod) = 'cash')")
    Double sumAllTimeCashExpense();

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month " +
           "AND e.paymentMethod IS NOT NULL " +
           "AND LOWER(e.paymentMethod) <> 'cash'")
    Double sumBankExpenseByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month " +
           "AND (e.paymentMethod IS NULL OR LOWER(e.paymentMethod) = 'cash')")
    Double sumCashExpenseByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Modifying
    @Transactional
    @Query("UPDATE Expense e SET e.category = :newName WHERE e.category = :oldName")
    int updateCategoryName(@Param("oldName") String oldName, @Param("newName") String newName);
}