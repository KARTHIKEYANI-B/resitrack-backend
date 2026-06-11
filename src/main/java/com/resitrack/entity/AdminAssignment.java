package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "admin_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Member.Position position;

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private LocalDate startDate = LocalDate.now();

    @Column(name = "end_date")
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "active_sentinel", nullable = true)
    private Integer activeSentinel = 1;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void setActive(boolean active) {
        this.active = active;
        this.activeSentinel = active ? 1 : null;
    }

    @PrePersist
    protected void onCreate() {
        this.activeSentinel = this.active ? 1 : null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.activeSentinel = this.active ? 1 : null;
        this.updatedAt = LocalDateTime.now();
    }
}