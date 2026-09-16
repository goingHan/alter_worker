package com.alertworker.gateway.service;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.enums.AlertType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: random alert payloads for the test-alert feature; fields referenced
// by the rule's match conditions are filled so the generated data is representative of a real alert
@Slf4j
@Service
@RequiredArgsConstructor
public class TestAlertDataGenerator {

    private static final String[] ALERT_NAMES = {"CPUHigh", "MemoryHigh", "DiskFull", "ServiceDown", "HighLatency"};
    private static final String[] SEVERITIES = {"critical", "warning", "info"};
    private static final String[] SERVICES = {"core", "gateway", "worker", "web", "db"};

    private final ObjectMapper objectMapper;

    public String generate(AlertRule rule) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("alertname", pick(ALERT_NAMES));
        fields.put("severity", pick(SEVERITIES));
        fields.put("instance", "node-" + ThreadLocalRandom.current().nextInt(1, 100) + ":9100");
        fields.put("job", "alertworker-test");

        for (Condition condition : resolveConditions(rule)) {
            if (!StringUtils.hasText(condition.key())) {
                continue;
            }
            if (condition.negated()) {
                fields.remove(condition.key());
                continue;
            }
            fields.put(condition.key(), StringUtils.hasText(condition.value())
                    ? condition.value()
                    : randomValue(condition.key()));
        }

        return rule.getAlertType() == AlertType.PROMETHEUS ? generatePrometheus(fields) : generateJson(fields);
    }

    private String generatePrometheus(Map<String, Object> fields) {
        Map<String, Object> labels = new LinkedHashMap<>(fields);
        String alertname = String.valueOf(labels.getOrDefault("alertname", "TestAlert"));
        String instance = String.valueOf(labels.getOrDefault("instance", "unknown"));
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("summary", alertname + " 告警（测试数据）");
        annotations.put("description", "实例 " + instance + " 触发 " + alertname);

        String startsAt = OffsetDateTime.now().minusMinutes(30).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("status", "firing");
        alert.put("labels", labels);
        alert.put("annotations", annotations);
        alert.put("startsAt", startsAt);
        alert.put("endsAt", "0001-01-01T00:00:00Z");
        alert.put("generatorURL", "http://prometheus.example.com:9090/graph");
        alert.put("fingerprint", String.format("%08x", ThreadLocalRandom.current().nextInt()));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "4");
        root.put("groupKey", "alertname=" + alertname);
        root.put("status", "firing");
        root.put("receiver", "alertworker");
        root.put("groupLabels", Map.of("alertname", alertname));
        root.put("commonLabels", labels);
        root.put("commonAnnotations", annotations);
        root.put("externalURL", "http://alertmanager.example.com:9093");
        root.put("alerts", List.of(alert));
        return write(root);
    }

    private String generateJson(Map<String, Object> fields) {
        String alertname = String.valueOf(fields.getOrDefault("alertname", "TestAlert"));
        String instance = String.valueOf(fields.getOrDefault("instance", "unknown"));
        fields.put("title", alertname + " 告警（测试数据）");
        fields.put("content", "实例 " + instance + " 触发 " + alertname);
        return write(fields);
    }

    private List<Condition> resolveConditions(AlertRule rule) {
        List<Condition> conditions = new ArrayList<>();
        if (StringUtils.hasText(rule.getMatchConditions())) {
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        rule.getMatchConditions(), new TypeReference<>() {});
                for (Map<String, Object> m : parsed) {
                    conditions.add(new Condition(
                            m.get("key") == null ? null : m.get("key").toString(),
                            m.get("value") == null ? null : m.get("value").toString(),
                            "NOT".equalsIgnoreCase(String.valueOf(m.get("op"))) || isTrue(m.get("not"))));
                }
            } catch (Exception e) {
                log.warn("Failed to parse match_conditions for rule {}: {}", rule.getId(), e.getMessage());
            }
        }
        if (conditions.isEmpty()) {
            conditions.add(new Condition(rule.getMatchKey(), rule.getMatchValue(), false));
        }
        return conditions;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String randomValue(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.contains("severity")) {
            return pick(SEVERITIES);
        }
        if (lower.contains("instance") || lower.contains("host") || lower.contains("node")) {
            return "node-" + ThreadLocalRandom.current().nextInt(1, 100) + ":9100";
        }
        if (lower.contains("value") || lower.contains("usage") || lower.contains("percent") || lower.contains("count")) {
            return String.format("%.1f", ThreadLocalRandom.current().nextDouble(50, 100));
        }
        if (lower.contains("service")) {
            return pick(SERVICES);
        }
        if (lower.contains("alertname")) {
            return pick(ALERT_NAMES);
        }
        return "test-" + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private String pick(String[] pool) {
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test payload", e);
        }
    }

    private record Condition(String key, String value, boolean negated) {
    }
}
