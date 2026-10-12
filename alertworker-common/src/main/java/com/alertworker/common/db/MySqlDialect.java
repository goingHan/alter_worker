package com.alertworker.common.db;

import com.baomidou.mybatisplus.annotation.DbType;

/**
 * MySQL dialect: named lock via {@code GET_LOCK}. Like the PostgreSQL advisory lock, a MySQL named lock is
 * connection-scoped, so acquire/hold/release must run on the same connection (see {@code ScheduledJobService}).
 */
public class MySqlDialect implements DatabaseDialect {

    private static final String LOCK_KEY = "alertworker_scheduler";

    @Override
    public DatabaseVendor vendor() {
        return DatabaseVendor.MYSQL;
    }

    @Override
    public String advisoryLockSql() {
        return "SELECT GET_LOCK('" + LOCK_KEY + "', 0)";
    }

    @Override
    public String advisoryUnlockSql() {
        return "SELECT RELEASE_LOCK('" + LOCK_KEY + "')";
    }

    @Override
    public DbType dbType() {
        return DbType.MYSQL;
    }
}
