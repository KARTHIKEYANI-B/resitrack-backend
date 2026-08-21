package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A single manually-entered health vital reading belonging to a Resident
 * (owner or family member — personal, not property-scoped). Covers both
 * Sugar Level and BP Level readings via a {@code readingType} discriminator,
 * rather than two near-identical tables. Purely a manual record store — no
 * automated diagnosis or advice is generated here.
 */
@Entity
@Table(name = "vital_readings", indexes = {
        @Index(name = "idx_vital_resident_id", columnList = "resident_id"),
        @Index(name = "idx_vital_type", columnList = "reading_type")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class VitalReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    /** SUGAR | BP */
    @Column(name = "reading_type", nullable = false, length = 10)
    private String readingType;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "reading_time")
    private LocalTime readingTime;

    // ── Sugar Level ──────────────────────────────────────────────────────
    @Column(name = "sugar_value", precision = 6, scale = 2)
    private BigDecimal sugarValue; // mg/dL

    /** Free text, e.g. Fasting / Post-Meal / Random — no fixed enum. */
    @Column(name = "sugar_context", length = 30)
    private String sugarContext;

    // ── BP Level ─────────────────────────────────────────────────────────
    @Column(name = "systolic")
    private Integer systolic;

    @Column(name = "diastolic")
    private Integer diastolic;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(length = 500)
    private String notes;

    /** Soft-delete flag — same convention as InsuranceDetail/LicenseDetail. */
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
