package com.alertworker.common.service;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                Instant instant = Instant.parse(obj.toString());
                ZoneId zone = args.length > 1 ? ZoneId.of(args[1]) : ZoneId.of("Asia/Shanghai");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args.length > 0 ? args[0] : "yyyy-MM-dd HH:mm:ss");
                return formatter.withZone(zone).format(instant);
            } catch (Exception e) {
                return obj.toString();
            }
        }

        @Override
        public String getName() {
            return "fmt_time";
        }
    }
}
