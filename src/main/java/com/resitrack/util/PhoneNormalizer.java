package com.resitrack.util;

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
