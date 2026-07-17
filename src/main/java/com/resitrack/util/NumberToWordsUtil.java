package com.resitrack.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a rupee amount into words using the Indian numbering system
 * (Crore / Lakh / Thousand), matching the "INR <Amount> Only" style used
 * on the Receipt Voucher / Payment Voucher reference formats.
 *
 * Mirrors frontend/src/utils/numberToWords.js so the UI preview and the
 * server-generated PDF always render identical "Amount in Words" text.
 */
public final class NumberToWordsUtil {

    private NumberToWordsUtil() {}

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        int t = n / 10;
        int o = n % 10;
        return TENS[t] + (o != 0 ? " " + ONES[o] : "");
    }

    private static String threeDigits(int n) {
        int h = n / 100;
        int rest = n % 100;
        StringBuilder out = new StringBuilder();
        if (h != 0) out.append(ONES[h]).append(" Hundred");
        if (rest != 0) {
            if (out.length() > 0) out.append(' ');
            out.append(twoDigits(rest));
        }
        return out.toString();
    }

    private static String integerToWords(long num) {
        if (num == 0) return "Zero";

        long crore = num / 10000000L;
        num %= 10000000L;
        long lakh = num / 100000L;
        num %= 100000L;
        long thousand = num / 1000L;
        num %= 1000L;
        long hundred = num;

        StringBuilder parts = new StringBuilder();
        if (crore != 0) parts.append(threeDigits((int) crore)).append(" Crore ");
        if (lakh != 0) parts.append(twoDigits((int) lakh)).append(" Lakh ");
        if (thousand != 0) parts.append(twoDigits((int) thousand)).append(" Thousand ");
        if (hundred != 0) parts.append(threeDigits((int) hundred));

        return parts.toString().trim().replaceAll("\\s+", " ");
    }

    /** e.g. 2065.00 -> "INR Two Thousand Sixty Five Only" */
    public static String amountInWords(BigDecimal amount) {
        return amountInWords(amount, "INR", "Only");
    }

    public static String amountInWords(BigDecimal amount, String prefix, String suffix) {
        BigDecimal value = (amount != null ? amount : BigDecimal.ZERO).abs();
        long rupees = value.setScale(0, RoundingMode.DOWN).longValue();
        int paise = value.subtract(BigDecimal.valueOf(rupees))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        StringBuilder words = new StringBuilder(integerToWords(rupees));
        if (paise > 0) {
            words.append(" and ").append(integerToWords(paise)).append(" Paise");
        }

        StringBuilder result = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) result.append(prefix).append(' ');
        result.append(words);
        if (suffix != null && !suffix.isEmpty()) result.append(' ').append(suffix);
        return result.toString().trim().replaceAll("\\s+", " ");
    }
}
