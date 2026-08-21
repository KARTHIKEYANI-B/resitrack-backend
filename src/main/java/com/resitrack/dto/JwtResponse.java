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

        // Viewer tier — read-only admin account. Frontend hides write controls;
        // backend enforces the restriction on every write endpoint regardless.
        private boolean viewer;

        // Admin "Owner" tier — deliberately NOT named `owner`/`isOwner` to
        // avoid colliding with the pre-existing, unrelated Resident concept
        // of the same name (residentRole == "OWNER" vs "FAMILY_MEMBER",
        // exposed as AuthContext's isOwner below). This field is about the
        // Admin-side role hierarchy (Owner > Super Admin > Admin), not
        // residency.
        private boolean systemOwner;

        private String residentRole;
        private Long   ownerResidentId;     
        private Long   familyMemberId;      
        private String relationship;        
    }
}