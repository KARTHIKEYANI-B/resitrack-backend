package com.resitrack.util;

/**
 * Normalizes phone numbers to a single canonical form before they are
 * written to the database or used to look up an existing record.
 *
 * Root cause this fixes: {@code findByPhone(...)} everywhere in this app is
 * a byte-exact SQL equality match, but phone numbers were being written
 * inconsistently across call sites — some paths only {@code .trim()}'d
 * (leaving internal spaces / dashes / a "+91" prefix intact), and a couple
 * (ResidentService admin/owner profile edits) didn't even trim. A number
 * stored as "98765 43210" would never match a login attempt of
 * "9876543210", even though both are visibly "the same number" to a human.
 *
 * Canonical form used here: digits only, with a leading Indian country
 * code (91) or trunk prefix (0) stripped when present, so all of these
 * normalize to the same 10-digit string:
 *   "98765 43210", "98765-43210", "+91 98765 43210", "919876543210",
 *   "09876543210", "9876543210"
 * -> "9876543210"
 *
 * This class only normalizes — it does not validate. Callers that need to
 * enforce "must be a 10-digit Indian mobile number" should keep doing that
 * validation themselves (client-side and/or server-side); this utility is
 * deliberately lenient so it never throws on odd legacy data (e.g. a test
 * fixture or a landline number of a different length) and can be applied
 * uniformly everywhere without new failure modes.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    /**
     * @param raw the phone number as typed/stored, possibly {@code null}
     * @return the normalized digits-only phone number, or {@code null} if
     *         {@code raw} is {@code null}/blank/contains no digits at all
     */
    public static String normalize(String raw) {
        if (raw == null) return null;

        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;

        if (digits.length() == 12 && digits.startsWith("91")) {
            // "+91 98765 43210" / "919876543210" -> "9876543210"
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            // "09876543210" (trunk prefix) -> "9876543210"
            digits = digits.substring(1);
        }

        return digits;
    }
}
