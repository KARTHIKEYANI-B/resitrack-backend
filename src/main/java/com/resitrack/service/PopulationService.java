package com.resitrack.service;

import com.resitrack.dto.PopulationStatsDTO;
import com.resitrack.entity.PropertyType;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.FamilyMemberRepository;
import com.resitrack.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopulationService {

    private final ResidentRepository    residentRepo;
    private final FamilyMemberRepository familyMemberRepo;
    private final AdminRepository       adminRepo;

    /**
     * Computes the Apartment Population Summary.
     *
     * Population = Unique Residents + Unique Family Members
     *
     * No double-counting:
     * - Owners = active non-deleted OWNER residents (counted once, regardless of roles)
     * - FamilyMembers = active family_members records (not login rows)
     * - Admins are only counted if they are NOT already counted as an Owner.
     *   An Admin with a non-null residentId is already included in the owner count.
     *
     * Formula:
     *   totalPopulation = totalOwners
     *                   + totalFamilyMembers
     *                   + adminsWithNoResidentLink   ← pure admins only
     *
     * Example:
     *   Karthikeyani is both OWNER and ADMIN → counted once via totalOwners
     *   Super-admin account with no resident row → counted once via adminsWithNoResidentLink
     */
    public PopulationStatsDTO getPopulationStats() {

        // ── Owners ─────────────────────────────────────────────────────────
        long totalFlatOwners  = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.FLAT);
        long totalVillaOwners = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.VILLA);
        long totalOwners      = totalFlatOwners + totalVillaOwners;

        // ── Family Members ──────────────────────────────────────────────────
        //
        // totalFamilyMembers = sum of all owners' registered familyMembers values
        //                      MINUS the owners themselves (each owner counted
        //                      separately in totalOwners).
        //
        // Example: owner registers familyMembers = 3 (= owner + 2 dependants)
        //   → contributes 2 non-owner family members to the population count.
        //
        // "With App Access" stays as-is: it counts family_member login accounts
        // that were explicitly given app access — that count is already correct.
        long sumRegisteredHouseholdSize = residentRepo.sumRegisteredFamilyMembersCount();
        // Subtract owners because the registered count includes the owner themselves
        long totalFamilyMembers       = Math.max(0, sumRegisteredHouseholdSize - totalOwners);
        long familyMembersWithAccess  = familyMemberRepo.countByActiveTrueAndHasAppAccessTrue();

        // ── Admins ──────────────────────────────────────────────────────────
        // totalAdmins and totalSuperAdmins must be mutually exclusive — each
        // admin row is either a plain Admin or a Super Admin, never counted
        // as both, so the two figures can be shown side-by-side without
        // double-counting or needing to be added together.
        long totalSuperAdmins = 0; // updated below if column exists
        try {
            totalSuperAdmins = adminRepo.countBySuperAdminTrue();
        } catch (Exception ignored) {
            // column may not exist in older schema — gracefully default to 0
        }
        long totalAdminAccounts = adminRepo.count();
        long totalAdmins        = Math.max(0, totalAdminAccounts - totalSuperAdmins);

        // Admins who are ALSO residents are already counted as Owners above.
        // Only admins with NO linked resident row are new, unique persons.
        long adminsWithNoResidentLink = adminRepo.countByResidentIdIsNull();

        // ── Active login users ─────────────────────────────────────────────
        // Owner active logins + family member active logins
        long activeOwnerLogins = residentRepo.countAllActiveNonDeleted();
        long activeFmLogins    = residentRepo.countActiveFamilyMemberLogins();
        long totalActiveUsers  = activeOwnerLogins + activeFmLogins;

        // ── Grand total ────────────────────────────────────────────────────
        // Each unique person is counted exactly once:
        //   - Owners (even if they also hold admin roles) → counted via totalOwners
        //   - Pure admins with no resident link          → counted via adminsWithNoResidentLink
        //   - Family Members (as people)                 → counted via totalFamilyMembers
        long totalPopulation = totalOwners + totalFamilyMembers + adminsWithNoResidentLink;

        // ── Age breakdown (from family_members.age) ────────────────────────
        long age0to12   = familyMemberRepo.countByAgeBetween(0, 12);
        long age13to17  = familyMemberRepo.countByAgeBetween(13, 17);
        long age18to25  = familyMemberRepo.countByAgeBetween(18, 25);
        long age26to40  = familyMemberRepo.countByAgeBetween(26, 40);
        long age41to60  = familyMemberRepo.countByAgeBetween(41, 60);
        long age60plus  = familyMemberRepo.countByAgeGte(61);
        long ageUnknown = familyMemberRepo.countByAgeNull();

        return PopulationStatsDTO.builder()
                .totalOwners(totalOwners)
                .totalFlatOwners(totalFlatOwners)
                .totalVillaOwners(totalVillaOwners)
                .totalFamilyMembers(totalFamilyMembers)
                .familyMembersWithAccess(familyMembersWithAccess)
                .totalAdmins(totalAdmins)
                .totalSuperAdmins(totalSuperAdmins)
                .totalActiveUsers(totalActiveUsers)
                .totalPopulation(totalPopulation)
                .ageGroup0to12(age0to12)
                .ageGroup13to17(age13to17)
                .ageGroup18to25(age18to25)
                .ageGroup26to40(age26to40)
                .ageGroup41to60(age41to60)
                .ageGroup60plus(age60plus)
                .ageUnknown(ageUnknown)
                .build();
    }
}