package com.alertworker.processor.scheduler;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.enums.AlertType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Extracts the reportable fields (severity / service_type / summary) of a persisted {@code alert_message} row.
 *
 * <p>Severity comes from the RULE (the rule owns the alert level, not the incoming payload); service_type/summary are
 * alert-type aware: JSON reads top-level keys, PROMETHEUS navigates {@code alerts[0].labels} /
 * {@code alerts[0].annotations} (mirrors {@code AlertRuleMatcher.extractValue} / {@code extractPrometheusLabel} —
 * multi-alert Prometheus payloads only carry the first alert's labels). Missing values fall back to {@code UNKNOWN}
 * (summary falls back to the messageNo).
 *
 * <p>Shared by {@link DailyReportJobExecutor} and {@link WarningDigestJobExecutor}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AlertRowExtractor {

    private static final String UNKNOWN = "UNKNOWN";

    private final ObjectMapper objectMapper;

    public RowInfo extract(AlertMessage row, AlertRule rule) {
        String severity = rule != null && rule.getSeverity() != null
                ? rule.getSeverity().name() : UNKNOWN;
        Map<String, Object> payload = parsePayload(row.getPayload());
        String serviceType = UNKNOWN;
        String summary = null;
        if (payload != null) {
            if (rule != null && rule.getAlertType() == AlertType.PROMETHEUS) {
                Map<?, ?> firstAlert = firstAlertMap(payload);
                Map<?, ?> labels = mapField(firstAlert, "labels");
                serviceType = orUnknown(readMapValue(labels, "service_type"));
                summary = readMapValue(mapField(firstAlert, "annotations"), "summary");
            } else {
                serviceType = orUnknown(stringValue(payload.get("service_type")));
                Object title = payload.get("title");
                Object content = payload.get("content");
                summary = title != null ? String.valueOf(title) : content != null ? String.valueOf(content) : null;
            }
        }
        if (summary == null || summary.isBlank()) {
            summary = row.getMessageNo();
        }
        return new RowInfo(severity, serviceType, summary);
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse alert_message payload for aggregation: {}", e.getMessage());
            return null;
        }
    }

    private Map<?, ?> firstAlertMap(Map<String, Object> payload) {
        Object alertsObj = payload.get("alerts");
        if (!(alertsObj instanceof List<?> alerts) || alerts.isEmpty()) {
            return null;
        }
        Object first = alerts.get(0);
        return first instanceof Map<?, ?> map ? map : null;
    }

    private Map<?, ?> mapField(Map<?, ?> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String readMapValue(Map<?, ?> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    public record RowInfo(String severity, String serviceType, String summary) {
    }
}
