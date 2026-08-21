package com.resitrack.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // Never serialized into API responses — this is a BCrypt hash, not
    // something any client should ever receive back.
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String phone;

    @Builder.Default
    @Column(name = "is_super_admin", nullable = false)
    private boolean superAdmin = false;

    // ── Owner tier (above Super Admin) ──────────────────────────────────
    // A System Owner has every permission Super Admin has (enforced by
    // OR-ing this flag into the existing superAdmin permission checks —
    // see AdminAccountController/AdminAssignmentController/MemberController/
    // SecurityController/PaymentService/NotificationController), plus
    // Owner-exclusive capabilities (managing Super Admin accounts, system
    // info). Deliberately kept independent of `superAdmin` rather than also
    // setting superAdmin=true, so the committee-position-driven single-
    // Super-Admin logic in AdminAssignmentService (which reassigns
    // superAdmin whenever the PRESIDENT position changes hands) can never
    // accidentally touch an Owner account — Owner is not a committee
    // position and must not be affected by presidency transfers.
    @Builder.Default
    @Column(name = "is_system_owner", nullable = false)
    private boolean systemOwner = false;

    // Mirrors Resident.isActive()/SecurityGuard.isActive() — lets an Owner
    // deactivate a Super Admin account (login-blocking) without deleting it.
    //
    // columnDefinition sets a SQL-level DEFAULT so a fresh install (where
    // Hibernate creates this whole table in one CREATE TABLE) gets it
    // right automatically. IMPORTANT for any EXISTING database that
    // already has admin rows before this column existed: MySQL backfills
    // a new NOT NULL column added via ALTER TABLE to 0/false for existing
    // rows regardless of this columnDefinition (Hibernate's ddl-auto=update
    // only ADDS the column, it doesn't apply the DEFAULT retroactively to
    // already-existing rows) — which would lock every existing admin out
    // of login via the AuthService.buildAdminResponse() check below. Run
    // `UPDATE admins SET is_active = 1 WHERE is_active = 0;` once, ONLY
    // BEFORE this admin.systemOwner column has ever been used to legitimately
    // deactivate anyone, immediately after this column is first created on
    // any pre-existing database (including production).
    // ── Viewer tier (read-only access, below regular Admin) ─────────────
    // A Viewer can log in and access all SuperAdmin/SystemOwner-visible
    // pages in read-only mode. Every write endpoint checks this flag and
    // returns 403 FORBIDDEN. Viewers are created and managed by the
    // System Owner or Super Admin via AdminAccountController.
    // Deliberately a separate flag (not superAdmin=false + position=null)
    // so existing regular admin accounts are never accidentally treated as
    // viewers, and so the check isViewer() is unambiguous.
    @Builder.Default
    @Column(name = "is_viewer", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean viewer = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean active = true;

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

    // The admin's own uploaded signature/seal image, stamped on receipts
    // they process (see ReceiptService — snapshotted onto Receipt.adminSignature
    // at generation time, not read live from here on every view). Both null
    // until the admin uploads one via AdminSignatureController; signaturePublicId
    // is the Cloudinary asset id, kept so a re-upload can delete the old one.
    @Column(name = "signature_url")
    private String signatureUrl;

    @Column(name = "signature_public_id")
    private String signaturePublicId;

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