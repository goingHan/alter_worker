package com.alertworker.common.config;

import java.time.ZoneId;
import java.util.TimeZone;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pins the JVM default timezone and Jackson's serialization timezone to Asia/Shanghai (CST),
 * so every timestamp persisted by the backend (LocalDateTime.now(), OffsetDateTime.now())
 * and every timestamp displayed over the REST API uses CST consistently.
 * All three services scan {@code com.alertworker}, so this configuration applies everywhere.
 */
@Configuration
public class TimezoneConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @PostConstruct
    public void initDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonTimezoneCustomizer() {
        return builder -> builder.timeZone(TimeZone.getTimeZone(ZONE));
    }
}
