package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "family_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relationship relationship;

    private Integer age;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "has_app_access", nullable = false)
    private boolean hasAppAccess = false;

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

    public enum Relationship {
        FATHER, MOTHER, WIFE, HUSBAND,
        SON, DAUGHTER, BROTHER, SISTER,
        GRANDFATHER, GRANDMOTHER, OTHER;

        public String getDisplayName() {
            return switch (this) {
                case FATHER      -> "Father";
                case MOTHER      -> "Mother";
                case WIFE        -> "Wife";
                case HUSBAND     -> "Husband";
                case SON         -> "Son";
                case DAUGHTER    -> "Daughter";
                case BROTHER     -> "Brother";
                case SISTER      -> "Sister";
                case GRANDFATHER -> "Grandfather";
                case GRANDMOTHER -> "Grandmother";
                case OTHER       -> "Other";
            };
        }
    }
}