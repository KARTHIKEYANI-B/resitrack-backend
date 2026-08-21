package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A doctor appointment record belonging to a Resident (owner or family
 * member — personal, not property-scoped). Manually entered by the resident;
 * no automated scheduling or diagnosis logic.
 */
@Entity
@Table(name = "doctor_appointments", indexes = {
        @Index(name = "idx_doctor_appt_resident_id", columnList = "resident_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DoctorAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "doctor_name", nullable = false, length = 150)
    private String doctorName;

    @Column(length = 150)
    private String specialization;

    @Column(name = "hospital_clinic", length = 200)
    private String hospitalClinic;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    @Column(length = 500)
    private String reason;

    @Column(length = 1000)
    private String notes;

    /** Free text: Scheduled | Completed | Cancelled. */
    @Column(length = 20)
    private String status;

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
