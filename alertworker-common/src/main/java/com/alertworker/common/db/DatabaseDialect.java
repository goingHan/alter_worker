package com.alertworker.common.db;

import com.baomidou.mybatisplus.annotation.DbType;

/**
 * Adapter over the small set of behaviours that differ between databases.
 *
 * <p>Only truly dialect-specific concerns live here (single-instance scheduler lock, MyBatis-Plus
 * pagination dialect). Anything expressible in portable SQL stays in the shared mappers; the raw
 * analytics SQL is isolated behind {@code AlertMessageAnalytics} implementations instead.
 */
public interface DatabaseDialect {

    /** The vendor this dialect implements. */
    DatabaseVendor vendor();

    /** SQL that must return a single boolean-ish value: truthy when the session-level lock was acquired. */
    String advisoryLockSql();

    /** SQL that releases the session-level lock acquired by {@link #advisoryLockSql()}. */
    String advisoryUnlockSql();

    /** MyBatis-Plus dialect used by the pagination interceptor. */
    DbType dbType();
}
