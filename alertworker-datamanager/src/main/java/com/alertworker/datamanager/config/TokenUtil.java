package com.alertworker.datamanager.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

// #AI-CODE-CLASS-dev_release/v0.0.1-20260901: replace hand-rolled JWT with the standard jjwt library
@Slf4j
@Component
public class TokenUtil {

    private static final long DEFAULT_EXPIRES_HOURS = 12;

    @Value("${alertworker.auth.secret:alertworker-auth-secret-2026}")
    private String secret;

    @Value("${alertworker.auth.expires-hours:12}")
    private long expiresHours;

    public String createToken(String username) {
        long hours = expiresHours > 0 ? expiresHours : DEFAULT_EXPIRES_HOURS;
        Date exp = new Date(System.currentTimeMillis() + hours * 3600_000L);
        return Jwts.builder()
                .subject(username)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public String verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token verification failed: {}", e.getMessage());
            return null;
        }
    }

    // Derive a stable 256-bit key from the configured secret (any length) so short
    // secrets still satisfy HS256's 32-byte minimum without forcing a long secret.
    private SecretKey signingKey() {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive signing key", e);
        }
    }
}
