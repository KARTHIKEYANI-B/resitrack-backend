package com.resitrack.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;

    /**
     * Optional client hint, e.g. "ANDROID" or "WEB" — stored on the issued
     * refresh token for device-tracking only. Never trusted for auth
     * decisions. Absent/blank falls back to "UNKNOWN" (see RefreshTokenService).
     */
    private String deviceType;
}
