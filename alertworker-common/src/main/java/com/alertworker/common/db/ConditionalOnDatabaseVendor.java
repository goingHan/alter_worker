package com.alertworker.common.db;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers the annotated bean only when the resolved {@link DatabaseVendor} matches {@link #value()}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnDatabaseVendorCondition.class)
public @interface ConditionalOnDatabaseVendor {

    DatabaseVendor value();
}
