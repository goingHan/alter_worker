package com.alertworker.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// #AI-CODE-FILE-dev_release/v0.0.1-20260826: DingDing robot HMAC sign support (customize-robot-security-settings)
public final class DingDingSignUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private DingDingSignUtil() {
    }

    public static String signUrl(String webhookUrl, String secret) {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            return webhookUrl + (webhookUrl.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign DingDing webhook URL", e);
        }
    }
}
