package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_settings")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double defaultMaintenanceAmount;
    private Integer defaultDueDay;         

    @Enumerated(EnumType.STRING)
    private RecurringCycle recurringCycle;

    private Double penaltyPercentage;
    private Double lateFeeAmount;
    private Integer gracePeriodDays;

    private String apartmentName;
    private String apartmentAddress;
    private String contactPhone;
    private String contactEmail;

    private String receiptFooterText;
    private String currencyFormat;          // default "INR"

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum RecurringCycle { MONTHLY, QUARTERLY, YEARLY }
}