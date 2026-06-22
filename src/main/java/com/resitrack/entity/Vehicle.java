package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single vehicle belonging to an Owner Resident.
 *
 * An Owner can have any number of Vehicle rows (two-wheeler, four-wheeler, etc.).
 * Each vehicle optionally carries its own insurance document (image or PDF)
 * plus the existing insurance number / expiry fields that were previously
 * stored once on the Resident entity itself.
 *
 * This entity is purely additive — it does NOT remove or replace the legacy
 * Resident.vehicleDetails / insuranceNumber / insuranceExpiryDate fields,
 * which continue to work exactly as before for backward compatibility.
 */
@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning resident (must be an OWNER, enforced in service layer). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "vehicle_number", nullable = false, length = 50)
    private String vehicleNumber;

    /** Optional descriptive type, e.g. TWO_WHEELER, FOUR_WHEELER, OTHER. */
    @Column(name = "vehicle_type", length = 30)
    private String vehicleType;

    /** Relative path under the upload root, e.g. "vehicle-insurance/uuid.pdf" */
    @Column(name = "insurance_document_path", length = 500)
    private String insuranceDocumentPath;

    /** Original filename, kept for display purposes when downloading. */
    @Column(name = "insurance_document_name", length = 255)
    private String insuranceDocumentName;

    /** Existing Insurance Number — same semantics as Resident.insuranceNumber. */
    @Column(name = "insurance_number", length = 100)
    private String insuranceNumber;

    /** Existing Insurance Provider — same semantics as Resident.insuranceProvider. */
    @Column(name = "insurance_provider", length = 200)
    private String insuranceProvider;

    /** Existing Insurance Expiry Date — same semantics as Resident.insuranceExpiryDate. */
    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

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
