package com.resitrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * RefreshToken
 * ─────────────────────────────────────────────────────────────────────────
 * Backs the "Remember Me" / auto-login feature for the Capacitor Android
 * app (and the web build). One row per issued refresh token — a user can
 * hold several valid rows at once (e.g. phone + tablet + browser), each
 * independently revocable.
 *
 * SECURITY NOTE: the `token` column stores a SHA-256 hex hash of the raw
 * refresh token, never the raw value itself. The raw token is generated
 * with SecureRandom, returned to the client exactly once (at login or
 * during a refresh), and is never persisted or logged anywhere. A leaked
 * database therefore cannot be used to mint working refresh tokens.
 *
 * `userId` + `role` together identify the account this token belongs to
 * (role is one of ADMIN | USER | SECURITY, mirroring the three JWT
 * authorities the app already issues — Owners and Family Members both
 * store role = "USER", matching UserDetailsServiceImpl/JwtResponse). This
 * app has no single unified "users" table, so a plain user_id FK isn't
 * enough on its own to resolve which table it points into.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the Admin / Resident / SecurityGuard row this token belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ADMIN | USER | SECURITY — which table user_id refers to (matches JWT role). */
    @Column(nullable = false, length = 20)
    private String role;

    /** JWT subject (email) this token was issued for — avoids re-resolving role/account on every refresh. */
    @Column(nullable = false, length = 190)
    private String username;

    /** SHA-256 hex hash (64 chars) of the raw refresh token. Never the raw value. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean revoked = false;

    /** Client-supplied hint, e.g. "ANDROID" or "WEB". Best-effort, never trusted for auth decisions. */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
