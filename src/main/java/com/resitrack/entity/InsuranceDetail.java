package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single insurance policy belonging to a Resident (owner or family member —
 * this is personal, not property-scoped). A resident may hold any number of
 * policies (Health, Life, Vehicle, Personal Accident, Other).
 *
 * Purely additive: does not touch the legacy Resident.insuranceProvider /
 * insuranceNumber / insuranceExpiryDate fields, nor the per-Vehicle insurance
 * fields on the Vehicle entity, both of which remain untouched.
 */
@Entity
@Table(name = "insurance_details", indexes = {
        @Index(name = "idx_insurance_resident_id", columnList = "resident_id"),
        @Index(name = "idx_insurance_policy_number", columnList = "policy_number")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InsuranceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "insurance_type", length = 30)
    private String insuranceType;

    @Column(length = 200)
    private String provider;

    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    @Column(name = "policy_holder_name", length = 150)
    private String policyHolderName;

    @Column(name = "insured_person_name", length = 150)
    private String insuredPersonName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "premium_amount", precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "premium_frequency", length = 20)
    private String premiumFrequency;

    @Column(name = "sum_insured", precision = 14, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "nominee_name", length = 150)
    private String nomineeName;

    @Column(name = "nominee_relationship", length = 100)
    private String nomineeRelationship;

    @Column(name = "nominee_contact", length = 20)
    private String nomineeContact;

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
