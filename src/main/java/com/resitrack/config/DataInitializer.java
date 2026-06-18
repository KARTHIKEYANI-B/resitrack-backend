package com.resitrack.config;

import com.resitrack.entity.Admin;
import com.resitrack.entity.Maintenance;
import com.resitrack.entity.Member;
import com.resitrack.repository.AdminAssignmentRepository;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.MaintenanceRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository            adminRepo;
    private final AdminAssignmentRepository  assignmentRepo;
    private final ResidentRepository         residentRepo;
    private final MaintenanceRepository      maintenanceRepo;
    private final MemberService              memberService;
    private final PasswordEncoder            passwordEncoder;

    // ── Canonical account emails ──────────────────────────────────────────────
    // These must match AdminAssignmentService.POSITION_EMAILS exactly.
    // When adding a new position, update both places.
    private static final String SUPER_ADMIN_EMAIL    = "superadmin@gmail.com";
    private static final String SUPER_ADMIN_NAME     = "Super Admin";
    private static final String SUPER_ADMIN_PASSWORD = "Superadmin@123";

    // Secondary Super Admin account — used for client demos and QA flow testing.
    // Mirrors initSuperAdmin() exactly: same role, same permissions, same encoder.
    // Must also be added to SUPER_ADMIN_EMAILS below or initSuperAdmin() will
    // strip its superAdmin flag back to false on the next restart.
    private static final String TEST_SUPER_ADMIN_EMAIL    = "test@gmail.com";
    private static final String TEST_SUPER_ADMIN_NAME     = "Test Super Admin";
    private static final String TEST_SUPER_ADMIN_PASSWORD = "Test@123";
    private static final String TEST_SUPER_ADMIN_PHONE    = "9999999999";

    // All emails that are allowed to hold is_super_admin = true.
    // initSuperAdmin() demotes any OTHER admin row that has the flag set,
    // so every canonical super-admin account must be listed here.
    private static final List<String> SUPER_ADMIN_EMAILS = List.of(
        SUPER_ADMIN_EMAIL,
        TEST_SUPER_ADMIN_EMAIL
    );

    private static final String VICE_PRESIDENT_EMAIL    = "vicepresident@gmail.com";
    private static final String VICE_PRESIDENT_PASSWORD = "Vicepresident@123";
    private static final String VICE_PRESIDENT_NAME     = "Vice President";

    private static final String SECRETARY_EMAIL    = "secretary@gmail.com";
    private static final String SECRETARY_PASSWORD = "Secratery@123";
    private static final String SECRETARY_NAME     = "Secretary";

    private static final String JOINT_SEC_EMAIL    = "joinsecratery@gmail.com";
    private static final String JOINT_SEC_PASSWORD = "Joinseratery@123";
    private static final String JOINT_SEC_NAME     = "Joint Secretary";

    private static final String TREASURER_EMAIL    = "treasurer@gmail.com";
    private static final String TREASURER_PASSWORD = "Treasurer@123";
    private static final String TREASURER_NAME     = "Treasurer";

    /**
     * Legacy emails that existed before the canonical gmail.com migration.
     * These are purged on every startup so only one account per position remains.
     * All five positions now have canonical gmail.com accounts; no apartment.com
     * account should survive past purgeLegacyAccounts().
     */
    private static final List<String> LEGACY_EMAILS = List.of(
        "admin.president@apartment.com",
        "admin.vicepresident@apartment.com",    // VP now migrated to vicepresident@gmail.com
        "admin.secretary@apartment.com",
        "admin.jointsecretary@apartment.com",
        "admin.treasurer@apartment.com",
        "admin@resitrack.com"       // original default admin from first-generation seed
    );

    @Override
    public void run(String... args) {
        purgeLegacyAccounts();           // remove all old apartment.com duplicates first
        initSuperAdmin();
        initTestSuperAdmin();            // secondary Super Admin account for demo/QA
        initDefaultPositionAccounts();   // create all 4 non-president canonical accounts
        initMaintenance();
        memberService.seedDefaultPositions();
        // NOTE: assignmentService.seedPositionAdminAccounts() is intentionally NOT called here.
        // That method uses the old apartment.com email map and would recreate duplicate accounts.
        // All canonical position accounts are now managed exclusively by initDefaultPositionAccounts().
        log.info("=== ResiTrack Data Initialization Complete ===");
    }

    // ── Step 1: Remove legacy duplicate accounts ──────────────────────────────
    /**
     * Deletes the old apartment.com admin accounts that were created by the original
     * AdminAssignmentService.seedPositionAdminAccounts() call.  Running this once
     * collapses each position back to a single canonical account.
     *
     * Safety rules:
     *  - Only deletes accounts whose email is in the known LEGACY_EMAILS list.
     *  - Deletes the account's AdminAssignment history rows first (FK constraint).
     *  - Skips any legacy account that somehow has an ACTIVE assignment (defensive guard).
     *  - Idempotent: if the account is already gone, does nothing.
     */
    @Transactional
    protected void purgeLegacyAccounts() {
        for (String email : LEGACY_EMAILS) {
            Optional<Admin> opt = adminRepo.findByEmail(email);
            if (opt.isEmpty()) {
                continue; // already gone — nothing to do
            }
            Admin legacy = opt.get();

            // Guard: skip if this account still has an active assignment
            // (should never happen, but protects against accidental data loss)
            boolean hasActiveAssignment = assignmentRepo
                    .findByAdminIdAndActiveTrue(legacy.getId())
                    .isPresent();
            if (hasActiveAssignment) {
                log.warn("Skipping purge of {} — it still has an active assignment. " +
                         "Revoke the assignment first.", email);
                continue;
            }

            // Delete all historical assignment rows for this admin (FK constraint)
            List<com.resitrack.entity.AdminAssignment> history =
                    assignmentRepo.findByAdmin(legacy);
            if (!history.isEmpty()) {
                assignmentRepo.deleteAll(history);
                log.info("Deleted {} assignment history row(s) for legacy account: {}",
                        history.size(), email);
            }

            adminRepo.delete(legacy);
            log.info("Purged legacy admin account: {}", email);
        }
    }

    // ── Step 2: Ensure Super Admin account exists ─────────────────────────────
    private void initSuperAdmin() {
        adminRepo.findAll().forEach(a -> {
            if (!SUPER_ADMIN_EMAILS.contains(a.getEmail()) && a.isSuperAdmin()) {
                a.setSuperAdmin(false);
                adminRepo.save(a);
                log.warn("Removed stale superAdmin flag from: {}", a.getEmail());
            }
        });

        if (!adminRepo.existsByEmail(SUPER_ADMIN_EMAIL)) {
            Admin superAdmin = Admin.builder()
                    .name(SUPER_ADMIN_NAME)
                    .email(SUPER_ADMIN_EMAIL)
                    .phone("")
                    .password(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                    .superAdmin(true)
                    .forcePasswordChange(false)
                    .build();
            adminRepo.save(superAdmin);
            log.info("Super Admin created: {} / {}", SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD);
        } else {
            Admin superAdmin = adminRepo.findByEmail(SUPER_ADMIN_EMAIL).get();
            boolean dirty = false;
            if (!superAdmin.isSuperAdmin())          { superAdmin.setSuperAdmin(true);          dirty = true; }
            if (superAdmin.isForcePasswordChange())  { superAdmin.setForcePasswordChange(false); dirty = true; }
            if (dirty) adminRepo.save(superAdmin);
            log.info("Super Admin account ready (existing password preserved): {}", SUPER_ADMIN_EMAIL);
        }
    }

    // ── Step 2b: Ensure secondary (test/demo) Super Admin account exists ──────
    /**
     * Creates a second, fully-equivalent Super Admin account for client demos
     * and QA flow testing, identified by TEST_SUPER_ADMIN_EMAIL.
     *
     * Mirrors initSuperAdmin() exactly:
     *  - Same builder shape, same PasswordEncoder (BCrypt, strength 12 — see
     *    SecurityConfig.passwordEncoder()), same superAdmin=true flag.
     *  - Idempotent: only creates the row if it doesn't already exist; never
     *    touches the password of an existing account with this email (so if
     *    someone later changes the password in-app, restarts won't revert it).
     *  - position is left null — this account is not tied to a committee seat,
     *    so it cannot collide with AdminAssignmentService's seat-holder logic.
     *  - Does not modify, delete, or affect any other admin row.
     */
    private void initTestSuperAdmin() {
        if (!adminRepo.existsByEmail(TEST_SUPER_ADMIN_EMAIL)) {
            Admin testSuperAdmin = Admin.builder()
                    .name(TEST_SUPER_ADMIN_NAME)
                    .email(TEST_SUPER_ADMIN_EMAIL)
                    .phone(TEST_SUPER_ADMIN_PHONE)
                    .password(passwordEncoder.encode(TEST_SUPER_ADMIN_PASSWORD))
                    .superAdmin(true)
                    .forcePasswordChange(false)
                    .build();
            adminRepo.save(testSuperAdmin);
            log.info("Test Super Admin created: {} / {}", TEST_SUPER_ADMIN_EMAIL, TEST_SUPER_ADMIN_PASSWORD);
        } else {
            Admin testSuperAdmin = adminRepo.findByEmail(TEST_SUPER_ADMIN_EMAIL).get();
            boolean dirty = false;
            if (!testSuperAdmin.isSuperAdmin())   { testSuperAdmin.setSuperAdmin(true); dirty = true; }
            if (dirty) adminRepo.save(testSuperAdmin);
            log.info("Test Super Admin account ready (existing password preserved): {}", TEST_SUPER_ADMIN_EMAIL);
        }
    }

    // ── Step 3: Ensure all position accounts exist (Secretary, VP, Joint Secretary, Treasurer) ─
    private void initDefaultPositionAccounts() {
        createPositionAccount(VICE_PRESIDENT_EMAIL, VICE_PRESIDENT_PASSWORD, VICE_PRESIDENT_NAME, Member.Position.VICE_PRESIDENT, false);
        createPositionAccount(SECRETARY_EMAIL,       SECRETARY_PASSWORD,       SECRETARY_NAME,       Member.Position.SECRETARY,       false);
        createPositionAccount(JOINT_SEC_EMAIL,       JOINT_SEC_PASSWORD,       JOINT_SEC_NAME,       Member.Position.JOINT_SECRETARY, false);
        createPositionAccount(TREASURER_EMAIL,       TREASURER_PASSWORD,       TREASURER_NAME,       Member.Position.TREASURER,       false);
    }

    private void createPositionAccount(String email, String password, String name,
                                        Member.Position position, boolean isSuperAdmin) {
        if (!adminRepo.existsByEmail(email)) {
            Admin admin = Admin.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .phone("")
                    .superAdmin(isSuperAdmin)
                    .forcePasswordChange(false)
                    .position(position)
                    .build();
            adminRepo.save(admin);
            log.info("Position admin account created: {} / {}", email, password);
        } else {
            Admin admin = adminRepo.findByEmail(email).get();
            boolean dirty = false;
            if (admin.getPosition() == null)        { admin.setPosition(position);          dirty = true; }
            if (admin.isForcePasswordChange())      { admin.setForcePasswordChange(false);  dirty = true; }
            if (dirty) adminRepo.save(admin);
            log.info("Position admin account ready (existing password preserved): {}", email);
        }
    }

    // ── Step 4: Default maintenance config ────────────────────────────────────
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
            log.info("Default maintenance config created");
        }
    }
}