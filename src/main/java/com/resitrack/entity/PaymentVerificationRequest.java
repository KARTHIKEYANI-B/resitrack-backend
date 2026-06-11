package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "screenshot_path", length = 500)
    private String screenshotPath;

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