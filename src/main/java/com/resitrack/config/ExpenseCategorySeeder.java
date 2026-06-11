package com.resitrack.config;

import com.resitrack.entity.ExpenseCategoryEntity;
import com.resitrack.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseCategorySeeder implements CommandLineRunner {

    private final ExpenseCategoryRepository categoryRepo;

    private static final List<String> BUILT_IN = List.of(
        "Building Maintenance",
        "CCTV Expenses",
        "Cultural or Community Events",
        "Electricity Expenses",
        "Expenses",
        "Filing & Legal Consultation Fees",
        "Freight and Carriage",
        "Genset Diesel Expenses",
        "Gift Expenses",
        "Hardware & Spares Expenses",
        "Lift Maintenance",
        "Minor Expenses",
        "Office Supplies and Printing",
        "Petrol Expenses",
        "Plumbing Maintenance",
        "Pooja Expenses",
        "Printing & Stationery Expenses",
        "RO Water",
        "RO Water Maintenance Expenses",
        "Security Maintenance Salary",
        "Security System CCTV",
        "Security Salary",
        "Staff Salary",
        "Staff Welfare",
        "STP Maintenance",
        "Telephone Expenses",
        "Wastage Disposal Expenses",
        "Bank Charges",
        "Computer Expenses"
    );

    @Override
    public void run(String... args) {
        int seeded = 0;
        for (String name : BUILT_IN) {
            if (!categoryRepo.existsByNameIgnoreCase(name)) {
                categoryRepo.save(ExpenseCategoryEntity.builder()
                        .name(name)
                        .builtIn(true)
                        .build());
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("ExpenseCategorySeeder: seeded {} built-in categories", seeded);
        }
    }
}