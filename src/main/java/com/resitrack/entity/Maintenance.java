package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String maintenanceType;

    @Column(name = "rate_per_sq_ft", precision = 10, scale = 4)
    private BigDecimal ratePerSqFt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal lateFee;

    @Column(nullable = false)
    private Boolean lateFeeEnabled = false;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}