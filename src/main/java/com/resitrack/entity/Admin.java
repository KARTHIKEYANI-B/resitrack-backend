package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;

    @Builder.Default
    @Column(name = "is_super_admin", nullable = false)
    private boolean superAdmin = false;

    @Builder.Default
    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "position")
    private Member.Position position;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "resident_id")
    private Long residentId;

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