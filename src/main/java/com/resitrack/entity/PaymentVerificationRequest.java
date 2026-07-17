package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment_verification_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false)
    private String submittedName;

    @Column(nullable = false)
    private String flatNumber;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "transaction_id", nullable = true, length = 200, columnDefinition = "VARCHAR(200) NULL")
    private String transactionId;

    @Builder.Default
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = "GPAY";

    @Column(name = "paid_to_admin_id")
    private Long paidToAdminId;

    @Column(name = "paid_to_admin_name", length = 150)
    private String paidToAdminName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    /** Cloudinary secure_url — permanent, publicly-resolvable HTTPS URL. */
    @Column(name = "screenshot_url", length = 500)
    private String screenshotUrl;

    /** Cloudinary public_id — kept so the asset can be deleted/managed later. */
    @Column(name = "screenshot_public_id", length = 200)
    private String screenshotPublicId;

    @Column(name = "screenshot_file_name")
    private String screenshotFileName;

    @Column(nullable = false)
    private String paymentMonth;

    @Column(name = "submitted_by_resident_id")
    @Builder.Default
    private Long submittedByResidentId = null;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    @Column(name = "payment_id")
    private Long paymentId;

    // ── Multi-Month Maintenance Payment ─────────────────────────────────
    // When true, this request covers 2+ billing months in one submission
    // and monthAllocations holds the per-month breakdown (see
    // PaymentVerificationRequestMonth). When false/null (every request
    // created by the ORIGINAL single-month submit/submit-cash/
    // submit-bank-transfer flows), monthAllocations stays empty and this
    // request behaves EXACTLY as before — paymentMonth/paymentAmount above
    // are the sole source of truth, unchanged.
    @Builder.Default
    @Column(name = "is_multi_month", nullable = false)
    private Boolean isMultiMonth = false;

    @Builder.Default
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentVerificationRequestMonth> monthAllocations = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum RequestStatus {
        PENDING,
        VERIFIED,
        REJECTED
    }
}