package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal penaltyAmount;

    @Builder.Default
    @Column(nullable = false)
    private boolean penaltyEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssignmentType assignmentType = AssignmentType.ALL;

    @Column(length = 2000)
    private String assignedFlats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BatchStatus status = BatchStatus.ACTIVE;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalAssigned = 0;

    // ── Persisted, batch-scoped payment counters ───────────────────────────
    // FIX: these used to be @Transient and were populated by a query that
    // counted ALL payments() for the calendar month — including regular
    // monthly maintenance — not just payments belonging to this batch.
    // They are now real columns, updated exclusively by BatchPaymentService
    // whenever a BatchPayment row for this batch changes status, so the
    // counts can ONLY ever reflect this specific batch's own payment records.
    @Builder.Default
    @Column(nullable = false)
    private Integer paidCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer unpaidCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum AssignmentType {
        ALL,
        BLOCK,
        VILLA_GROUP,
        INDIVIDUAL
    }

    public enum BatchStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}