package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    private String name;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(length = 150)
    private String email;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean placeholder = true;

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

    public enum Position {
        PRESIDENT,
        VICE_PRESIDENT,
        SECRETARY,
        JOINT_SECRETARY,
        TREASURER;

        public String getDisplayName() {
            return switch (this) {
                case PRESIDENT       -> "President";
                case VICE_PRESIDENT  -> "Vice President";
                case SECRETARY       -> "Secretary";
                case JOINT_SECRETARY -> "Joint Secretary";
                case TREASURER       -> "Treasurer";
            };
        }
    }
}