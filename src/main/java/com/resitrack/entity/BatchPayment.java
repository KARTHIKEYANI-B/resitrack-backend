package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BatchPayment
 * ─────────────────────────────────────────────────────────────────────────
 * One row per (MaintenanceBatch × Resident-property) created automatically
 * when a Maintenance Batch is created.
 *
 * This is intentionally a SEPARATE table from `payments` (the regular
 * monthly-maintenance ledger). Mixing the two was the root cause of the
 * bug being fixed here: the old code derived a batch's paid/unpaid counts
 * by querying the generic `payments` table filtered only by calendar
 * month, which silently included regular monthly-maintenance payments.
 *
 * Each BatchPayment row is scoped to exactly one batch (batch_id FK) and
 * exactly one property (resident_id = the OWNER resident, never an FM
 * login row), so paid/unpaid counts for a batch are always computed from
 * rows that belong ONLY to that batch.
 *
 * "Paid By" supports an Owner or a Family Member paying on behalf of the
 * property: paidByResidentId/paidByName/paidByRole record who actually
 * submitted the payment, while resident_id always stays the property
 * owner so the batch's per-property paid/unpaid accounting is unambiguous.
 */
@Entity
@Table(name = "batch_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_batch_resident", columnNames = {"batch_id", "resident_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BatchPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private MaintenanceBatch batch;

    /** Always the property OWNER resident — the billing unit for the batch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BatchPaymentStatus status = BatchPaymentStatus.UNPAID;

    // ── Submission details (filled when Owner/FM submits a payment) ───────
    private String paymentMethod;          // UPI, BANK_TRANSFER, CASH
    private String transactionId;
    private LocalDate submittedDate;

    /** The actual resident (owner OR family member) who submitted/paid. */
    @Column(name = "paid_by_resident_id")
    private Long paidByResidentId;

    @Column(name = "paid_by_name")
    private String paidByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_by_role")
    private Resident.ResidentRole paidByRole;

    // ── Verification (filled when Admin verifies) ──────────────────────────
    @Column(name = "verified_date")
    private LocalDate verifiedDate;

    @Column(name = "verified_by_admin_id")
    private Long verifiedByAdminId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum BatchPaymentStatus {
        UNPAID,
        PENDING_VERIFICATION,
        PAID,
        REJECTED
    }
}
