package com.resitrack.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityResidentDTO {

    private Long   id;
    private String ownerName;
    private String flatNumber;
    private String phone;
    private String flatType;
    private String propertyType;

    private List<FamilyMemberInfo> familyMembers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FamilyMemberInfo {
        private Long   id;
        private String name;
        private String relationship;
        private String phone;
        private Integer age;
    }
}