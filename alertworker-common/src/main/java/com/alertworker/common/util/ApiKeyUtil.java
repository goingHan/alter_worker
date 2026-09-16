package com.alertworker.common.util;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class ApiKeyUtil {

    private static final String PREFIX = "aw-";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyUtil() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }
}
