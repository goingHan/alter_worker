package com.alertworker.common.db;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public class OnDatabaseVendorCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnDatabaseVendor.class.getName());
        if (attributes == null) {
            return false;
        }
        Object value = attributes.get("value");
        DatabaseVendor required = value instanceof DatabaseVendor vendor
                ? vendor
                : DatabaseVendor.valueOf(String.valueOf(value));
        DatabaseVendor actual = DatabaseVendorResolver.resolve(context.getEnvironment());
        return actual == required;
    }
}
