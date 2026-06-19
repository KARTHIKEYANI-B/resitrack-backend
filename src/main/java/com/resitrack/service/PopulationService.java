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


    public PopulationStatsDTO getPopulationStats() {

        // ── Owners ─────────────────────────────────────────────────────────
        long totalFlatOwners  = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.FLAT);
        long totalVillaOwners = residentRepo.countActiveNonDeletedByPropertyType(PropertyType.VILLA);
        long totalOwners      = totalFlatOwners + totalVillaOwners;


        long sumRegisteredHouseholdSize = residentRepo.sumRegisteredFamilyMembersCount();
        long totalFamilyMembers       = Math.max(0, sumRegisteredHouseholdSize - totalOwners);
        long familyMembersWithAccess  = familyMemberRepo.countByActiveTrueAndHasAppAccessTrue();

        long totalSuperAdmins = 0; // updated below if column exists
        try {
            totalSuperAdmins = adminRepo.countBySuperAdminTrue();
        } catch (Exception ignored) {
        }
        long totalAdminAccounts = adminRepo.count();
        long totalAdmins        = Math.max(0, totalAdminAccounts - totalSuperAdmins);
        long adminsWithNoResidentLink = adminRepo.countByResidentIdIsNull();

        long activeOwnerLogins = residentRepo.countAllActiveNonDeleted();
        long activeFmLogins    = residentRepo.countActiveFamilyMemberLogins();
        long totalActiveUsers  = activeOwnerLogins + activeFmLogins;

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