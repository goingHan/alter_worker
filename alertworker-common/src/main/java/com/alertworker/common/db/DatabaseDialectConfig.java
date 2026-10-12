package com.alertworker.common.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Registers the single {@link DatabaseDialect} bean matching the resolved vendor. */
@Configuration
public class DatabaseDialectConfig {

    @Bean
    public DatabaseDialect databaseDialect(Environment environment) {
        DatabaseVendor vendor = DatabaseVendorResolver.resolve(environment);
        return vendor == DatabaseVendor.MYSQL ? new MySqlDialect() : new PostgresDialect();
    }
}
