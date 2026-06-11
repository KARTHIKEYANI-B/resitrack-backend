package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id")
    private Maintenance maintenance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private LocalDate paymentDate;

    private String paymentMethod;

    @Column(unique = true)
    private String transactionId;

    private String submittedResidentName;
    private String submittedRegisterNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.NOT_VERIFIED;

    private String rejectionReason;

    private String paymentMonth;
    private String paymentYear;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean adminCreated = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING,
        PENDING_VERIFICATION,
        PAID,
        OVERDUE
    }

    public enum VerificationStatus {
        NOT_VERIFIED,
        PENDING,
        VERIFIED,
        REJECTED
    }
}
