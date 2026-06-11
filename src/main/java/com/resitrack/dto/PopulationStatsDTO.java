package com.resitrack.dto;

import lombok.*;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PopulationStatsDTO {

    private long totalOwners;          
    private long totalFlatOwners;      
    private long totalVillaOwners;     

    private long totalFamilyMembers;   
    private long familyMembersWithAccess; 

    private long totalAdmins;          
    private long totalSuperAdmins;     

    private long totalActiveUsers;    

    private long totalPopulation;

    private long ageGroup0to12;
    private long ageGroup13to17;
    private long ageGroup18to25;
    private long ageGroup26to40;
    private long ageGroup41to60;
    private long ageGroup60plus;
    private long ageUnknown;           
}