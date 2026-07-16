package com.resitrack.util;

import java.util.Comparator;

/**
 * Natural-order string comparator for flat/villa numbers.
 *
 * These are stored as plain strings ("1", "42", "A-101"), so a default
 * String sort is lexicographic — "10" sorts before "2". This comparator
 * compares digit runs numerically and non-digit runs case-insensitively,
 * chunk by chunk, so "9" sorts before "10" and "A-2" sorts before "A-10".
 */
public final class NaturalOrderComparator {

    private NaturalOrderComparator() {
    }

    public static final Comparator<String> INSTANCE = NaturalOrderComparator::compare;

    public static int compare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=\\d)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++; j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }
}
