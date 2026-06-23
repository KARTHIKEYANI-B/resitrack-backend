package com.resitrack.dto;

import com.resitrack.entity.FamilyMember;
import lombok.*;

import java.time.LocalDateTime;

public class FamilyMemberDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private Long          residentId;
        private String        name;
        private String        relationship;
        private String        relationshipDisplayName;
        private Integer       age;
        private String        phone;
        private String        email;
        private boolean       hasAppAccess;
        private Long          userId;          
        private String        loginEmail;      
        private boolean       active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(FamilyMember fm, String loginEmail) {
            return Response.builder()
                    .id(fm.getId())
                    .residentId(fm.getResident().getId())
                    .name(fm.getName())
                    .relationship(fm.getRelationship().name())
                    .relationshipDisplayName(fm.getRelationship().getDisplayName())
                    .age(fm.getAge())
                    .phone(fm.getPhone())
                    .email(fm.getEmail())
                    .hasAppAccess(fm.isHasAppAccess())
                    .userId(fm.getUserId())
                    .loginEmail(loginEmail)
                    .active(fm.isActive())
                    .createdAt(fm.getCreatedAt())
                    .updatedAt(fm.getUpdatedAt())
                    .build();
        }

        public static Response from(FamilyMember fm) {
            return from(fm, null);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Request {
        private String  name;         // required
        private String  relationship; // required — FamilyMember.Relationship name
        private Integer age;
        private String  phone;
        private String  email;        // contact email (optional)
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GrantAccessRequest {
        private String loginEmail;    // email for logging in (required, must be unique)
        private String password;      // initial password (required, min 8 chars)
    }
}