package com.resitrack.service;

import com.resitrack.entity.RefreshToken;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * RefreshTokenService
 * ─────────────────────────────────────────────────────────────────────────
 * Issues, validates, and revokes the refresh tokens backing "Remember Me" /
 * auto-login. Raw tokens are 256-bit SecureRandom values; only their
 * SHA-256 hash is ever persisted or looked up, so a database leak alone
 * cannot be used to authenticate. Raw values and hashes are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepo;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    /** Issues and persists a new refresh token, returning the RAW value (never persisted as-is). */
    @Transactional
    public String issue(String role, Long userId, String username, String deviceType) {
        String rawToken = generateRawToken();

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .role(role)
                .username(username)
                .token(hash(rawToken))
                .expiresAt(LocalDateTime.now().plus(refreshExpirationMillis, ChronoUnit.MILLIS))
                .revoked(false)
                .deviceType(normalizeDeviceType(deviceType))
                .build();

        refreshTokenRepo.save(entity);
        log.info("Issued refresh token for {} (role={}, device={})", username, role, entity.getDeviceType());
        return rawToken;
    }

    /**
     * Validates a raw refresh token (not expired, not revoked, exists) and
     * bumps its last_used_at. Throws CustomException(401) on any failure —
     * invalid, expired, and revoked are all reported as one generic
     * "invalid or expired" message so a caller can't use the response to
     * distinguish "revoked" from "made up" tokens.
     */
    @Transactional
    public RefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank())
            throw new CustomException("Refresh token is required", HttpStatus.BAD_REQUEST);

        RefreshToken rt = refreshTokenRepo.findByToken(hash(rawToken))
                .orElseThrow(() -> new CustomException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        if (Boolean.TRUE.equals(rt.getRevoked()))
            throw new CustomException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);

        if (rt.getExpiresAt() == null || rt.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new CustomException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);

        rt.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepo.save(rt);
        return rt;
    }

    /**
     * Revokes a refresh token so it (and any future access token minted
     * from it) can never be used again. Silently no-ops for an
     * unknown/already-revoked token — logout must always succeed from the
     * client's point of view, even if the token was already cleared or
     * this is a duplicate call.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepo.findByToken(hash(rawToken)).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
        });
    }

    private String normalizeDeviceType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) return "UNKNOWN";
        String trimmed = deviceType.trim().toUpperCase();
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK-mandatory algorithm; this can never actually happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
