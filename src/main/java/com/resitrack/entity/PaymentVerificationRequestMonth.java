package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentVerificationRequestMonth
 * ─────────────────────────────────────────────────────────────────────────
 * One row per (PaymentVerificationRequest × billing month) — powers the
 * Multi-Month Maintenance Payment feature.
 *
 * A single screenshot/cash/bank-transfer submission from an Owner or Family
 * Member can now cover several billing months (e.g. May + June paid
 * together in July). Each selected month is stored here with the exact
 * amount allocated to it (the remaining balance for a partially-paid month,
 * or the full monthly amount for an unpaid month).
 *
 * On admin verification (PaymentVerificationService.verifyRequest), ONE
 * Payment row is created PER row here — each carrying its own
 * paymentMonth/amount — so Maintenance Summary, Paid/Unpaid Detail, and
 * Pending Dues all continue to work exactly as before, with no changes to
 * their own month-wise logic. paymentDate on every generated Payment row is
 * the verification date (today), so Financial Summary's collection ledger
 * (which groups by paymentDate, not paymentMonth) naturally reports the
 * whole multi-month amount in the month it was actually collected —
 * exactly per spec ("Do NOT split financial collection into previous
 * months").
 *
 * A single-month request (the existing, unmodified submission flow) never
 * creates any rows here — verifyRequest() falls back to its original
 * single-Payment-row behaviour whenever a request has no allocations.
 */
@Entity
@Table(name = "payment_verification_request_months")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentVerificationRequestMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private PaymentVerificationRequest request;

    /** Billing month this allocation pays toward, e.g. "2026-05". */
    @Column(name = "payment_month", nullable = false, length = 7)
    private String paymentMonth;

    /** Amount allocated to this specific month (remaining balance or full amount). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Set once the admin verifies the parent request and a Payment row is created for this month. */
    @Column(name = "payment_id")
    private Long paymentId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
