package com.alertworker.common.db;

/**
 * Supported database vendors. The active vendor is resolved once at startup from
 * {@code alertworker.database.dialect} (explicit) or the JDBC URL, and drives every
 * dialect-specific code path.
 */
public enum DatabaseVendor {
    POSTGRESQL,
    MYSQL
}
