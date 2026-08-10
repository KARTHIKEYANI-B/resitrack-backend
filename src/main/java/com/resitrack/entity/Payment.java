package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Correlates every Payment row created from the same "Add Payment"
    // submission (one row per billing month covered) — e.g. selecting
    // Apr/May/Jun in one multi-month admin entry gives all 3 resulting rows
    // the same paymentBatchId. Null for older rows created before this
    // column existed, and for any single-month payment made through a path
    // other than PaymentService.registerAdminPayment. Used to build a
    // consolidated multi-month receipt (see ReceiptService) instead of N
    // separate single-month receipts for what the resident experiences as
    // one payment.
    private String paymentBatchId;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean adminCreated = false;

    /**
     * True only for an admin "Record Payment" entry covering more than one
     * billing month. paymentMonth/amount above still hold the FIRST selected
     * month and the FULL entered total (so every existing single-row report
     * — Financial Summary, Payment Management, Receipts, Payment History —
     * keeps showing the undivided amount unchanged); monthAllocations below
     * carries the per-month breakdown used only by Maintenance Summary,
     * Paid/Unpaid Details and Pending Dues.
     */
    @Builder.Default
    @Column(name = "is_multi_month", nullable = false)
    private Boolean isMultiMonth = false;

    @Builder.Default
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentMonthAllocation> monthAllocations = new ArrayList<>();

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
