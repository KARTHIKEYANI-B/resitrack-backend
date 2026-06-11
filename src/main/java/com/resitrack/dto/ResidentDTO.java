package com.resitrack.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResidentDTO {
    private Long          id;
    private String        fullName;
    private String        email;
    private String        phone;
    private String        flatNumber;
    private String        flatType;        // descriptive unit type (2BHK, 3BHK…)
    private String        propertyType;    // "FLAT" | "VILLA"
    private Double        squareFeet;
    private Integer       familyMembers;
    private Integer       age;
    private String        vehicleDetails;
    private String        address;
    private String        status;          // ACTIVE | INACTIVE
    private String        registrationStatus; // PENDING | APPROVED | REJECTED
    private boolean       isApproved;
    private boolean       registered;
    private LocalDateTime createdAt;

    private String        profilePhotoUrl;

    private String    insuranceProvider;
    private String    insuranceNumber;
    private LocalDate insuranceExpiryDate;

    private boolean   taxesReminderEnabled;

    private String    ebReferenceNumber;
    private LocalDate ebDueDate;

    private String    waterTaxReferenceNumber;
    private LocalDate waterTaxDueDate;

    private String    buildingTaxReferenceNumber;
    private LocalDate buildingTaxDueDate;

    private String    taxesInsuranceReferenceNumber;
    private LocalDate taxesInsuranceDueDate;
}