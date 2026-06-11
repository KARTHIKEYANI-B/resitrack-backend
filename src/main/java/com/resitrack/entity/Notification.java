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
        TAXES_REMINDER
    }
}