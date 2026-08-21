package com.resitrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * Body of PUT /user/personal-details. Deliberately excludes email (login
 * identity, not editable here) and profilePhotoUrl (own dedicated
 * upload/remove endpoints under /user/profile/photo).
 */
@Data
public class PersonalDetailsUpdateRequest {

    private static final String PHONE_PATTERN = "^$|^[+]?[0-9]{10,13}$";

    // ── Basic Information ────────────────────────────────────────────────
    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name is too long")
    private String fullName;

    @Size(max = 20, message = "Gender is too long")
    private String gender;

    @PastOrPresent(message = "Date of birth cannot be a future date")
    private LocalDate dateOfBirth;

    @Size(max = 10, message = "Blood group is too long")
    private String bloodGroup;

    @Size(max = 100, message = "Nationality is too long")
    private String nationality;

    @Size(max = 20, message = "Marital status is too long")
    private String maritalStatus;

    @Size(max = 150, message = "Occupation is too long")
    private String occupation;

    @Size(max = 200, message = "Qualification is too long")
    private String qualification;

    // ── Identification ────────────────────────────────────────────────────
    @Pattern(regexp = "^$|^[0-9]{12}$", message = "Aadhaar number must be 12 digits")
    private String aadhaarNumber;

    // ── Contact Information ──────────────────────────────────────────────
    @NotBlank(message = "Primary phone number is required")
    @Pattern(regexp = "^[+]?[0-9]{10,13}$", message = "Invalid primary phone number")
    private String phone;

    @Pattern(regexp = PHONE_PATTERN, message = "Invalid alternate phone number")
    private String alternatePhone;

    @Pattern(regexp = PHONE_PATTERN, message = "Invalid WhatsApp number")
    private String whatsappNumber;

    // ── Address ───────────────────────────────────────────────────────────
    @Size(max = 100, message = "House/Flat number is too long")
    private String houseNumber;

    @Size(max = 150, message = "Building/Block is too long")
    private String buildingBlock;

    @Size(max = 200, message = "Street is too long")
    private String street;

    @Size(max = 150, message = "Area is too long")
    private String area;

    @Size(max = 100, message = "City is too long")
    private String city;

    @Size(max = 100, message = "State is too long")
    private String state;

    @Size(max = 100, message = "Country is too long")
    private String country;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN code must be 6 digits")
    private String pincode;

    // ── Emergency Contact ────────────────────────────────────────────────
    @Size(max = 150, message = "Emergency contact name is too long")
    private String emergencyContactName;

    @Size(max = 100, message = "Emergency contact relationship is too long")
    private String emergencyContactRelationship;

    @Pattern(regexp = PHONE_PATTERN, message = "Invalid emergency contact phone number")
    private String emergencyContactPhone;

    @Pattern(regexp = PHONE_PATTERN, message = "Invalid emergency contact alternate phone number")
    private String emergencyContactAlternatePhone;

    @Size(max = 500, message = "Emergency contact address is too long")
    private String emergencyContactAddress;
}
