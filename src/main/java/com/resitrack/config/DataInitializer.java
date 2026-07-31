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

    // NOTE: These must stay in sync with the canonical position-email maps in
    // AdminAssignmentService.POSITION_EMAILS and MemberService.resolvePositionEmail() —
    // all three places encode the same "one email per committee position" mapping.
    private static final String SUPER_ADMIN_EMAIL    = "superadmin@gmail.com";
    private static final String SUPER_ADMIN_NAME     = "Super Admin";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@123";


    private static final String TEST_SUPER_ADMIN_EMAIL    = "test@gmail.com";
    private static final String TEST_SUPER_ADMIN_NAME     = "Test Super Admin";
    private static final String TEST_SUPER_ADMIN_PASSWORD = "Admin@123";
    private static final String TEST_SUPER_ADMIN_PHONE    = "";

    // System Owner — the top of the role hierarchy (Owner > Super Admin >
    // Admin), seeded the same way as every other admin account here
    // (hashed via passwordEncoder, never stored in plaintext). Its "email"
    // is a plain identifier string, not a real email address — the login
    // DTO/endpoints apply no @Email or format validation (see
    // LoginRequest.java), so this logs in exactly like any other admin via
    // the existing email/phone identifier field. It is a NORMAL row in the
    // admins table: it appears in the admin list and every other admin
    // query like any other admin — not hidden from admin-side tooling
    // (Super Admins can still see and are aware of it), so it stays
    // auditable. Only non-Super-Admin position admins have it filtered out
    // of the accounts list (see AdminAccountController).
    //
    // Deliberately does NOT get superAdmin=true (see Admin.java's
    // `systemOwner` field javadoc for why: it must stay independent of the
    // committee-position-driven single-Super-Admin logic in
    // AdminAssignmentService). Every Super-Admin-gated permission check
    // across the app has been updated to also accept systemOwner==true, so
    // this account still has every Super Admin capability plus the
    // Owner-exclusive ones (see AdminAccountController).
    // public: AdminAccountController reads this to exclude the account from
    // the admin list for non-Owner, non-Super-Admin callers.
    public static final String OWNER_EMAIL     = "security@apmsys";
    private static final String OWNER_NAME     = "System Owner";
    private static final String OWNER_PASSWORD = "Security@apmsys123";

    private static final List<String> SUPER_ADMIN_EMAILS = List.of(
        SUPER_ADMIN_EMAIL,
        TEST_SUPER_ADMIN_EMAIL
    );

    private static final List<String> OWNER_EMAILS = List.of(
        OWNER_EMAIL
    );

    private static final String VICE_PRESIDENT_EMAIL    = "vicepresident@gmail.com";
    private static final String VICE_PRESIDENT_PASSWORD = "Admin@123";
    private static final String VICE_PRESIDENT_NAME     = "Vice President";

    private static final String SECRETARY_EMAIL    = "secretary@gmail.com";
    private static final String SECRETARY_PASSWORD = "Admin@123";
    private static final String SECRETARY_NAME     = "Secretary";

    private static final String JOINT_SEC_EMAIL    = "joinsecratery@gmail.com";
    private static final String JOINT_SEC_PASSWORD = "Admin@123";
    private static final String JOINT_SEC_NAME     = "Joint Secretary";

    private static final String TREASURER_EMAIL    = "treasurer@gmail.com";
    private static final String TREASURER_PASSWORD = "Admin@123";
    private static final String TREASURER_NAME     = "Treasurer";

    private static final List<String> LEGACY_EMAILS = List.of(
        "admin.president@apartment.com",
        "admin.vicepresident@apartment.com",    
        "admin.secretary@apartment.com",
        "admin.jointsecretary@apartment.com",
        "admin.treasurer@apartment.com",
        "admin@resitrack.com"       
    );

    @Override
    public void run(String... args) {
        purgeLegacyAccounts();
        initSuperAdmin();
        initTestSuperAdmin();
        initSystemOwner();
        initDefaultPositionAccounts();
        initMaintenance();
        memberService.seedDefaultPositions();
        log.info("=== ResiTrack Data Initialization Complete ===");
    }


    @Transactional
    protected void purgeLegacyAccounts() {
        for (String email : LEGACY_EMAILS) {
            Optional<Admin> opt = adminRepo.findByEmail(email);
            if (opt.isEmpty()) {
                continue; // already gone — nothing to do
            }
            Admin legacy = opt.get();

            boolean hasActiveAssignment = assignmentRepo
                    .findByAdminIdAndActiveTrue(legacy.getId())
                    .isPresent();
            if (hasActiveAssignment) {
                log.warn("Skipping purge of {} — it still has an active assignment. " +
                         "Revoke the assignment first.", email);
                continue;
            }

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

    private void initSystemOwner() {
        // Stale-flag cleanup, same pattern as initSuperAdmin() above: strip
        // systemOwner from anything not in the allow-list, so the flag can
        // never end up on the wrong row.
        adminRepo.findAll().forEach(a -> {
            if (!OWNER_EMAILS.contains(a.getEmail()) && a.isSystemOwner()) {
                a.setSystemOwner(false);
                adminRepo.save(a);
                log.warn("Removed stale systemOwner flag from: {}", a.getEmail());
            }
        });

        if (!adminRepo.existsByEmail(OWNER_EMAIL)) {
            Admin owner = Admin.builder()
                    .name(OWNER_NAME)
                    .email(OWNER_EMAIL)
                    .phone("")
                    .password(passwordEncoder.encode(OWNER_PASSWORD))
                    .systemOwner(true)
                    .superAdmin(false)
                    .forcePasswordChange(false)
                    .build();
            adminRepo.save(owner);
            log.info("System Owner created: {} / {}", OWNER_EMAIL, OWNER_PASSWORD);
        } else {
            Admin owner = adminRepo.findByEmail(OWNER_EMAIL).get();
            boolean dirty = false;
            if (!owner.isSystemOwner())          { owner.setSystemOwner(true);          dirty = true; }
            // One-time rename from the pre-Owner-tier display name. Only
            // touches it if it still exactly matches the old default —
            // never overwrites a name someone has since customized.
            if ("Emergency Admin".equals(owner.getName())) { owner.setName(OWNER_NAME); dirty = true; }
            // Migrates the pre-Owner-tier version of this account, which
            // was seeded with superAdmin=true instead — see Admin.java's
            // `systemOwner` field javadoc for why Owner must stay
            // independent of superAdmin going forward.
            if (owner.isSuperAdmin())            { owner.setSuperAdmin(false);          dirty = true; }
            if (owner.isForcePasswordChange())   { owner.setForcePasswordChange(false); dirty = true; }
            if (dirty) adminRepo.save(owner);
            log.info("System Owner account ready (existing password preserved): {}", OWNER_EMAIL);
        }
    }

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