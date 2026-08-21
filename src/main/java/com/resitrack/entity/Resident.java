package com.resitrack.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "residents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    // Never serialized into API responses — this is a BCrypt hash, not
    // something any client should ever receive back. (Was previously
    // leaking through GET /admin/approvals and the approve/reject
    // endpoints, which return this entity directly.)
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String flatNumber;

    private String flatType;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    @Builder.Default
    private PropertyType propertyType = PropertyType.FLAT;

    @Column(name = "sq_ft")
    private Double sqFt;

    private Integer familyMembers;
    private Integer age;
    private String vehicleDetails;

    @Column(name = "insurance_provider", length = 200)
    private String insuranceProvider;

    @Column(name = "insurance_number", length = 100)
    private String insuranceNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(unique = true)
    private String registerNumber;

    private String address;
    private String adminEmail;

    @Column(name = "profile_photo", length = 500)
    private String profilePhoto;

    @Builder.Default
    @Column(nullable = false)
    private boolean isApproved = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus registrationStatus = RegistrationStatus.PENDING;

    private Long approvedByAdminId;
    private LocalDateTime approvedAt;

    @Column(length = 1000)
    private String rejectedReason;

    @Builder.Default
    @Column(nullable = false)
    private boolean registered = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private ResidentStatus status = ResidentStatus.INACTIVE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "resident_role", nullable = false)
    private ResidentRole residentRole = ResidentRole.OWNER;

    @Column(name = "owner_resident_id")
    private Long ownerResidentId;

    @Column(name = "family_member_id")
    private Long familyMemberId;

    @Builder.Default
    @Column(name = "taxes_reminder_enabled", nullable = false)
    private boolean taxesReminderEnabled = false;

    @Column(name = "eb_reference_number", length = 100)
    private String ebReferenceNumber;

    @Column(name = "eb_due_date")
    private LocalDate ebDueDate;

    @Column(name = "water_tax_reference_number", length = 100)
    private String waterTaxReferenceNumber;

    @Column(name = "water_tax_due_date")
    private LocalDate waterTaxDueDate;

    @Column(name = "building_tax_reference_number", length = 100)
    private String buildingTaxReferenceNumber;

    @Column(name = "building_tax_due_date")
    private LocalDate buildingTaxDueDate;

    @Column(name = "taxes_insurance_reference_number", length = 100)
    private String taxesInsuranceReferenceNumber;

    @Column(name = "taxes_insurance_due_date")
    private LocalDate taxesInsuranceDueDate;

    // ── Personal Management: Basic Info ────────────────────────────────
    @Column(length = 20)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(length = 100)
    private String nationality;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(length = 150)
    private String occupation;

    @Column(length = 200)
    private String qualification;

    // ── Personal Management: Identification ──────────────────────────────
    @Column(name = "aadhaar_number", length = 20)
    private String aadhaarNumber;

    // ── Personal Management: Contact Info (additive to phone/email above) ──
    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    // ── Personal Management: Structured Address ─────────────────────────
    // Additive — the legacy free-text `address` field above is untouched
    // and still used by registration/admin views.
    @Column(name = "house_number", length = 100)
    private String houseNumber;

    @Column(name = "building_block", length = 150)
    private String buildingBlock;

    @Column(length = 200)
    private String street;

    @Column(length = 150)
    private String area;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @Column(length = 10)
    private String pincode;

    // ── Personal Management: Emergency Contact ───────────────────────────
    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relationship", length = 100)
    private String emergencyContactRelationship;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_alternate_phone", length = 20)
    private String emergencyContactAlternatePhone;

    @Column(name = "emergency_contact_address", length = 500)
    private String emergencyContactAddress;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ResidentRole {
        OWNER,
        FAMILY_MEMBER
    }

    public enum ResidentStatus {
        ACTIVE,
        INACTIVE
    }

    public enum RegistrationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}