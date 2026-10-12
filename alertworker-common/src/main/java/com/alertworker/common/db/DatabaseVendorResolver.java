package com.alertworker.common.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * Resolves the active {@link DatabaseVendor}. Precedence:
 * <ol>
 *   <li>explicit {@code alertworker.database.dialect} = {@code postgresql} | {@code mysql};</li>
 *   <li>the JDBC URL prefix of {@code spring.datasource.url};</li>
 *   <li>default {@link DatabaseVendor#POSTGRESQL}.</li>
 * </ol>
 * Resolution is intentionally environment-only (no DataSource access) so it can be used from
 * {@code @Conditional} evaluation before any bean is instantiated.
 */
@Slf4j
public final class DatabaseVendorResolver {

    public static final String DIALECT_PROPERTY = "alertworker.database.dialect";

    private DatabaseVendorResolver() {
    }

    public static DatabaseVendor resolve(Environment environment) {
        String explicit = environment.getProperty(DIALECT_PROPERTY);
        if (explicit != null && !explicit.isBlank() && !"auto".equalsIgnoreCase(explicit.trim())) {
            DatabaseVendor vendor = parse(explicit.trim());
            if (vendor != null) {
                log.info("Database dialect forced by {}={} -> {}", DIALECT_PROPERTY, explicit, vendor);
                return vendor;
            }
            log.warn("Unrecognised {}={} (expected postgresql|mysql|auto), falling back to URL detection",
                    DIALECT_PROPERTY, explicit);
        }

        String url = environment.getProperty("spring.datasource.url");
        DatabaseVendor fromUrl = fromJdbcUrl(url);
        if (fromUrl != null) {
            log.info("Database dialect detected from JDBC URL -> {}", fromUrl);
            return fromUrl;
        }

        log.warn("Could not detect database dialect (spring.datasource.url={}), defaulting to {}",
                url, DatabaseVendor.POSTGRESQL);
        return DatabaseVendor.POSTGRESQL;
    }

    static DatabaseVendor fromJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.toLowerCase();
        if (u.startsWith("jdbc:mysql:") || u.startsWith("jdbc:mariadb:")) {
            return DatabaseVendor.MYSQL;
        }
        if (u.startsWith("jdbc:postgresql:")) {
            return DatabaseVendor.POSTGRESQL;
        }
        return null;
    }

    private static DatabaseVendor parse(String value) {
        return switch (value.toLowerCase()) {
            case "postgresql", "postgres", "pg" -> DatabaseVendor.POSTGRESQL;
            case "mysql", "mariadb" -> DatabaseVendor.MYSQL;
            default -> null;
        };
    }
}
