package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single license belonging to a Resident (owner or family member — personal,
 * not property-scoped). A resident may hold any number of licenses (Driving
 * License, Professional License, Other).
 */
@Entity
@Table(name = "license_details", indexes = {
        @Index(name = "idx_license_resident_id", columnList = "resident_id"),
        @Index(name = "idx_license_number", columnList = "license_number")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LicenseDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "license_type", length = 30)
    private String licenseType;

    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    @Column(name = "holder_name", length = 150)
    private String holderName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Comma-separated when a Driving License covers multiple classes. */
    @Column(name = "vehicle_classes", length = 200)
    private String vehicleClasses;

    @Column(name = "issuing_authority", length = 150)
    private String issuingAuthority;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String status;

    /** Soft-delete flag — same convention as {@link Vehicle#isActive()}. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

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
}
