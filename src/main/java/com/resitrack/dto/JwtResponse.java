package com.resitrack.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtResponse {

    /** Legacy alias for accessToken — kept so any existing caller reading `.token` keeps working unchanged. */
    private String token;

    @Builder.Default
    private String type = "Bearer";

    // ── Remember Me / auto-login ────────────────────────────────────────
    private String accessToken;
    private String refreshToken;
    private Long   expiresIn; // access token validity, in seconds

    private UserInfo user;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserInfo {

        private Long   id;
        private String name;
        private String email;
        private String role;            
        private String flatNumber;
        private String registerNumber;
        private String registrationStatus;
        private String flatType;
        private String propertyType;

        private boolean superAdmin;         

        private String residentRole;        
        private Long   ownerResidentId;     
        private Long   familyMemberId;      
        private String relationship;        
    }
}