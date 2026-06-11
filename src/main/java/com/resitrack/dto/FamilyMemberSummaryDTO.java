package com.resitrack.dto;

import com.resitrack.entity.FamilyMember;
import lombok.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FamilyMemberSummaryDTO {

    private Long          id;
    private String        name;
    private String        relationship;
    private String        relationshipDisplayName;
    private Integer       age;
    private String        phone;
    private String        email;
    private boolean       hasAppAccess;
    private String        loginEmail;    // null if no app access
    private LocalDateTime createdAt;

    public static FamilyMemberSummaryDTO from(FamilyMember fm, String loginEmail) {
        return FamilyMemberSummaryDTO.builder()
                .id(fm.getId())
                .name(fm.getName())
                .relationship(fm.getRelationship().name())
                .relationshipDisplayName(fm.getRelationship().getDisplayName())
                .age(fm.getAge())
                .phone(fm.getPhone())
                .email(fm.getEmail())
                .hasAppAccess(fm.isHasAppAccess())
                .loginEmail(loginEmail)
                .createdAt(fm.getCreatedAt())
                .build();
    }
}