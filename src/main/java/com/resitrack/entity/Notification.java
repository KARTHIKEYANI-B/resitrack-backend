package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private Long   targetResidentId;
    private String residentName;
    private String flatNumber;

    @Column(name = "target_admin_id")
    private Long   targetAdminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_property_type")
    private PropertyType targetPropertyType;

    private String     transactionId;
    private Long       paymentId;
    private BigDecimal paymentAmount;
    private String     paymentMethod;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private String recipientRole;

    // ── Personal Management expiry reminders (Phase 3) ─────────────────────
    // Nullable/additive — only populated for type = EXPIRY_REMINDER. These
    // three together form the duplicate-prevention key described in the
    // Phase 3 spec (user is targetResidentId, already present above).
    @Column(name = "related_record_type", length = 20)
    private String relatedRecordType;   // INSURANCE | LICENSE | DOCUMENT

    @Column(name = "related_record_id")
    private Long relatedRecordId;

    @Column(name = "reminder_days")
    private Integer reminderDays;       // 30 | 15 | 7 | 0 (0 = on-expiry)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        PAYMENT_REMINDER,
        FEE_WARNING,
        COMPLAINT,
        ANNOUNCEMENT,
        PENDING_DUE,
        PAYMENT_RECEIVED,
        PAYMENT_VERIFICATION,
        PAYMENT_APPROVED,
        PAYMENT_REJECTED,
        REGISTRATION,
        PAYMENT,
        REMINDER,
        INSURANCE_REMINDER,
        TAXES_REMINDER,
        EXPIRY_REMINDER
    }
}