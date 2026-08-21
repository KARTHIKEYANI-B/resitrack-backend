package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A manually-created reminder belonging to a Resident (owner or family
 * member — personal, not property-scoped), e.g. "renew health checkup",
 * "buy diabetes medication". Fires a single {@link Notification} (type
 * REMINDER) on its {@code reminderDate} via the existing
 * {@link com.resitrack.service.ReminderSchedulerService} single scheduled
 * entry point — no second notification system is created.
 */
@Entity
@Table(name = "personal_reminders", indexes = {
        @Index(name = "idx_reminder_resident_id", columnList = "resident_id"),
        @Index(name = "idx_reminder_date", columnList = "reminder_date")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false, length = 200)
    private String title;

    /** Free text, e.g. Medical, Insurance, Document, General — no fixed enum. */
    @Column(length = 50)
    private String category;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    /** Soft-delete flag — same convention as InsuranceDetail/LicenseDetail. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
