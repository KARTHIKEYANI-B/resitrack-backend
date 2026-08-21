package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A single diet-chart entry belonging to a Resident (owner or family
 * member — personal, not property-scoped). Purely a manual record of what
 * the resident chooses to note about their diet — no automated nutrition
 * advice is generated here.
 */
@Entity
@Table(name = "diet_chart_entries", indexes = {
        @Index(name = "idx_diet_resident_id", columnList = "resident_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DietChartEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    /** Free text, e.g. Monday, Weekday Plan — no fixed enum. */
    @Column(length = 100)
    private String title;

    /** Free text, e.g. Breakfast / Lunch / Dinner / Snacks — no fixed enum. */
    @Column(name = "meal_type", length = 50)
    private String mealType;

    @Column(nullable = false, length = 1000)
    private String description;

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
