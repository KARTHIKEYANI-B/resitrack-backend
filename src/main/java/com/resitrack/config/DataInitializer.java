package com.resitrack.config;

import com.resitrack.entity.Admin;
import com.resitrack.entity.Maintenance;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.service.AdminAssignmentService;
import com.resitrack.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository         adminRepo;
    private final ResidentRepository      residentRepo;
    private final MaintenanceRepository   maintenanceRepo;
    private final MemberService           memberService;
    private final AdminAssignmentService  assignmentService;
    private final PasswordEncoder         passwordEncoder;

    @Override
    public void run(String... args) {
        initSuperAdmin();
        initDefaultAdmin();
        initMaintenance();
        memberService.seedDefaultPositions();  
        assignmentService.seedPositionAdminAccounts();
        log.info("=== ResiTrack Data Initialization Complete ===");
    }

    private void initSuperAdmin() {
        final String SUPER_ADMIN_EMAIL    = "superadmin@gmail.com";
        final String SUPER_ADMIN_NAME     = "Super Admin";
        final String SUPER_ADMIN_PASSWORD = "Superadmin@123";

        adminRepo.findAll().forEach(a -> {
            if (!SUPER_ADMIN_EMAIL.equals(a.getEmail()) && a.isSuperAdmin()) {
                a.setSuperAdmin(false);
                adminRepo.save(a);
                log.warn("Removed stale superAdmin flag from: {}", a.getEmail());
            }
        });

        Admin superAdmin = adminRepo.findByEmail(SUPER_ADMIN_EMAIL).orElseGet(() -> {
            log.info("Super Admin account not found — creating: {}", SUPER_ADMIN_EMAIL);
            return Admin.builder()
                    .name(SUPER_ADMIN_NAME)
                    .email(SUPER_ADMIN_EMAIL)
                    .phone("")
                    .build();
        });

        superAdmin.setName(SUPER_ADMIN_NAME);
        superAdmin.setPassword(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
        superAdmin.setSuperAdmin(true);
        superAdmin.setForcePasswordChange(false);

        adminRepo.save(superAdmin);
        log.info("Super Admin account ready: {} (superAdmin=true, forcePasswordChange=false)",
                SUPER_ADMIN_EMAIL);
    }

    private void initDefaultAdmin() {
        if (!adminRepo.existsByEmail("admin@resitrack.com")) {
            Admin admin = Admin.builder()
                    .name("Admin")
                    .email("admin@resitrack.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .phone("9000000000")
                    .superAdmin(false)
                    .forcePasswordChange(false)
                    .build();
            adminRepo.save(admin);
            log.info("Default Admin created: admin@resitrack.com");
        }
    }

    private void initMaintenance() {
        if (maintenanceRepo.count() == 0) {
            Maintenance maintenance = Maintenance.builder()
                    .maintenanceType("Monthly")
                    .amount(BigDecimal.valueOf(3000))
                    .lateFee(BigDecimal.valueOf(100))
                    .lateFeeEnabled(true)
                    .dueDate(LocalDate.now().withDayOfMonth(10))
                    .active(true)
                    .build();
            maintenanceRepo.save(maintenance);
            log.info("Maintenance created Successfully");
        }
    }
}