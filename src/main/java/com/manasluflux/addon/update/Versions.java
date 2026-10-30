package com.manasluflux.addon.update;

import java.util.Locale;

/**
 * Dot-separated numeric version comparison, kept free of any game classes so it can be tested
 * on its own. 1.15.10 is newer than 1.15.9, a leading "v" is ignored, and any suffix from the
 * first non-digit/non-dot character on is dropped, so "1.15.5-beta" compares as 1.15.5.
 */
final class Versions {
    private Versions() {}

    static boolean isNewer(String candidate, String current) {
        int[] a = parts(candidate);
        int[] b = parts(current);
        int length = Math.max(a.length, b.length);

        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parts(String version) {
        if (version == null) return new int[0];

        String cleaned = version.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("v")) cleaned = cleaned.substring(1);

        int cut = cleaned.length();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c != '.' && !Character.isDigit(c)) {
                cut = i;
                break;
            }
        }

        if (cut == 0) return new int[0];

        String[] split = cleaned.substring(0, cut).split("\\.");
        int[] out = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                out[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
