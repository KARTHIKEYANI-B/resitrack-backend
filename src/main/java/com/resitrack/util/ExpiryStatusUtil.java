package com.resitrack.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Shared Active / Expiring Soon / Expired classification for Insurance,
 * License, and Personal Document records (Personal Management Phase 3).
 *
 * A meaningful manual status (Cancelled / Suspended) always wins over the
 * date-derived classification and is never silently overwritten — e.g. a
 * Cancelled policy with a future expiry date stays "Cancelled", not "Active".
 */
public final class ExpiryStatusUtil {

    /** Default "Expiring Soon" window, in days before expiry. */
    public static final int EXPIRING_SOON_WINDOW_DAYS = 30;

    /** The four supported reminder checkpoints, in days-before-expiry (0 = on expiry). */
    public static final int[] REMINDER_CHECKPOINTS = { 30, 15, 7, 0 };

    private ExpiryStatusUtil() {
    }

    /**
     * @param manualStatus the user-set status field (may be null/blank)
     * @param expiryDate   the record's expiry date (may be null)
     * @return one of: the manual status verbatim (if Cancelled/Suspended),
     *         "Expired", "Expiring Soon", or "Active"
     */
    public static String computeEffectiveStatus(String manualStatus, LocalDate expiryDate) {
        if (manualStatus != null) {
            String s = manualStatus.trim();
            if (s.equalsIgnoreCase("Cancelled") || s.equalsIgnoreCase("Suspended")) {
                return s;
            }
        }

        if (expiryDate == null) {
            return (manualStatus == null || manualStatus.isBlank()) ? "Active" : manualStatus.trim();
        }

        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) return "Expired";
        long daysLeft = ChronoUnit.DAYS.between(today, expiryDate);
        if (daysLeft <= EXPIRING_SOON_WINDOW_DAYS) return "Expiring Soon";
        return "Active";
    }

    /** True when the manual status is a meaningful override that expiry logic must never touch. */
    public static boolean isManualOverride(String manualStatus) {
        if (manualStatus == null) return false;
        String s = manualStatus.trim();
        return s.equalsIgnoreCase("Cancelled") || s.equalsIgnoreCase("Suspended");
    }
}
