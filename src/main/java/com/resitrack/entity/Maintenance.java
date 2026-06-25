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

    // Nullable by design: a NULL propertyType marks the legacy,
    // property-agnostic shared-rate row (applies to both Flat and Villa
    // owners) that MaintenanceService falls back to when no property-specific
    // (FLAT or VILLA) active row has been configured yet — see
    // getActiveMaintenanceConfig(PropertyType) in MaintenanceService.
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type")
    private PropertyType propertyType;

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