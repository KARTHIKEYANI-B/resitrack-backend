package com.resitrack.repository;

import com.resitrack.entity.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, Long> {

    List<ExpenseCategoryEntity> findAllByOrderByNameAsc();

    Optional<ExpenseCategoryEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}