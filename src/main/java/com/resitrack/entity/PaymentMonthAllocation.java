package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One billing month covered by a single multi-month admin-recorded Payment.
 *
 * A multi-month "Record Payment" entry (e.g. ₹9,000 for Apr+May+Jun) is
 * stored as ONE Payment row (amount=9000, so Financial Summary / Payment
 * Management / Receipts / Payment History all show the undivided total
 * exactly as entered) plus one PaymentMonthAllocation row per selected
 * month (each carrying its own share, e.g. ₹3,000). Maintenance Summary,
 * Paid/Unpaid Details and Pending Dues read these allocation rows (via
 * PaymentRepository.sumPaidAmountByPropertyAndPaymentMonth) to correctly
 * credit each billing month separately, while the parent Payment's own
 * (paymentMonth, amount) stays untouched for the undivided-total screens.
 */
@Entity
@Table(name = "payment_month_allocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentMonthAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payment_month", nullable = false)
    private String paymentMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
