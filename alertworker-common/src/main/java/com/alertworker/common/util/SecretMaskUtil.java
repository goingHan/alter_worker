package com.alertworker.common.util;

public final class SecretMaskUtil {

    private static final String MASK = "****";

    private SecretMaskUtil() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + MASK + value.substring(value.length() - 3);
    }

    public static boolean isMasked(String value) {
        return value != null && value.contains(MASK);
    }
}
