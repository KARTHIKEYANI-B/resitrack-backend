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

    @Transient
    private Integer totalPaid;

    @Transient
    private Integer totalPending;

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