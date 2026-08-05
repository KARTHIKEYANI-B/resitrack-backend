package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String receiptNumber; // e.g. REC-2025-00001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false)
    private String residentName;

    @Column(nullable = false)
    private String flatNumber;

    // Denormalized copy of the resident's propertyType at receipt-creation
    // time (same pattern as flatNumber/residentName above) — nullable so
    // ddl-auto=update's ALTER TABLE ADD COLUMN doesn't try to backfill every
    // existing row with a NOT NULL default (see Admin.active's javadoc for
    // why that's dangerous). Null is treated as FLAT everywhere it's read,
    // matching the same null-safety convention already used for
    // Resident.propertyType elsewhere in this codebase.
    @Enumerated(EnumType.STRING)
    private PropertyType propertyType;

    private String residentPhone;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal lateFeeAmount;

    private String paymentMethod;
    private String transactionId;

    private String apartmentName;
    private String adminSignature;
    private String receiptFooter;

    @CreationTimestamp
    private LocalDateTime generatedAt;
}
