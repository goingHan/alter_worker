package com.alertworker.common.service;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Slf4j
@Service
public class JinjavaTemplateService {

    private final Jinjava jinjava;

    public JinjavaTemplateService() {
        this.jinjava = new Jinjava();
        this.jinjava.registerFilter(new FmtTimeFilter());
    }

    public String render(String template, Map<String, Object> context) {
        try {
            return jinjava.render(template, context);
        } catch (Exception e) {
            log.error("Failed to render template, error: {}", e.getMessage(), e);
            throw new RuntimeException("Template render failed: " + e.getMessage(), e);
        }
    }

    static class FmtTimeFilter implements Filter {

        @Override
        public Object filter(Object obj, JinjavaInterpreter interpreter, String... args) {
            if (obj == null || obj.toString().isBlank()) {
                return "";
            }
            try {
                Instant instant = parseInstant(obj.toString());
                ZoneId zone = args.length > 1 ? ZoneId.of(args[1]) : ZoneId.of("Asia/Shanghai");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args.length > 0 ? args[0] : "yyyy-MM-dd HH:mm:ss");
                return formatter.withZone(zone).format(instant);
            } catch (Exception e) {
                return obj.toString();
            }
        }

        /**
         * Parses an ISO-8601 timestamp into an {@link Instant}.
         * Accepts both UTC instants (e.g. {@code 2026-10-08T07:58:07.314Z}) and offset-bearing
         * timestamps (e.g. {@code 2026-10-08T15:58:07.314+08:00}) produced by
         * {@link OffsetDateTime#now()} under the Asia/Shanghai default timezone.
         */
        private static Instant parseInstant(String text) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                return OffsetDateTime.parse(text).toInstant();
            }
        }

        @Override
        public String getName() {
            return "fmt_time";
        }
    }
}
