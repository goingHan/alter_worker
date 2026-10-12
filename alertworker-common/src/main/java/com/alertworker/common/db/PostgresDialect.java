package com.alertworker.common.db;

import com.baomidou.mybatisplus.annotation.DbType;

/** PostgreSQL dialect: session-level advisory lock via {@code pg_try_advisory_lock}. */
public class PostgresDialect implements DatabaseDialect {

    private static final String LOCK_KEY = "alertworker_scheduler";

    @Override
    public DatabaseVendor vendor() {
        return DatabaseVendor.POSTGRESQL;
    }

    @Override
    public String advisoryLockSql() {
        return "SELECT pg_try_advisory_lock(hashtext('" + LOCK_KEY + "')::bigint)";
    }

    @Override
    public String advisoryUnlockSql() {
        return "SELECT pg_advisory_unlock(hashtext('" + LOCK_KEY + "')::bigint)";
    }

    @Override
    public DbType dbType() {
        return DbType.POSTGRE_SQL;
    }
}
