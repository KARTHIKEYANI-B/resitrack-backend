package com.resitrack.dto;

import lombok.*;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalDetailsDTO {

    // ── Basic Information ────────────────────────────────────────────────
    private String    profilePhotoUrl;
    private String    fullName;
    private String    gender;
    private LocalDate dateOfBirth;
    private String    bloodGroup;
    private String    nationality;
    private String    maritalStatus;
    private String    occupation;
    private String    qualification;

    // ── Identification ────────────────────────────────────────────────────
    private String    aadhaarNumber;

    // ── Contact Information ──────────────────────────────────────────────
    private String phone;              // primary phone (read/write, shared with account phone)
    private String alternatePhone;
    private String email;              // read-only (login identity)
    private String whatsappNumber;

    // ── Address ───────────────────────────────────────────────────────────
    private String houseNumber;
    private String buildingBlock;
    private String street;
    private String area;
    private String city;
    private String state;
    private String country;
    private String pincode;

    // ── Emergency Contact ────────────────────────────────────────────────
    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactPhone;
    private String emergencyContactAlternatePhone;
    private String emergencyContactAddress;
}
