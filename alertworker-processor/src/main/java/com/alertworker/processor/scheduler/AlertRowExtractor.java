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
 * Extracts the reportable fields (severity / service_type / summary / content) of a persisted
 * {@code alert_message} row.
 *
 * <p>Severity comes from the RULE (the rule owns the alert level, not the incoming payload); service_type/summary/
 * content are alert-type aware: JSON reads top-level keys ({@code title} -> summary, {@code content} -> content),
 * PROMETHEUS navigates {@code alerts[0].labels} / {@code alerts[0].annotations}
 * ({@code summary}/{@code description}). Missing values fall back to {@code UNKNOWN}
 * (summary falls back to the messageNo, content falls back to the summary).
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
        String content = null;
        if (payload != null) {
            if (rule != null && rule.getAlertType() == AlertType.PROMETHEUS) {
                Map<?, ?> firstAlert = firstAlertMap(payload);
                Map<?, ?> labels = mapField(firstAlert, "labels");
                serviceType = orUnknown(readMapValue(labels, "service_type"));
                Map<?, ?> annotations = mapField(firstAlert, "annotations");
                summary = readMapValue(annotations, "summary");
                content = readMapValue(annotations, "description");
            } else {
                serviceType = orUnknown(stringValue(payload.get("service_type")));
                Object title = payload.get("title");
                Object contentObj = payload.get("content");
                summary = title != null ? String.valueOf(title) : contentObj != null ? String.valueOf(contentObj) : null;
                content = contentObj != null ? String.valueOf(contentObj) : summary;
            }
        }
        if (summary == null || summary.isBlank()) {
            summary = row.getMessageNo();
        }
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: fall back to the summary so the digest detail always has text
        if (content == null || content.isBlank()) {
            content = summary;
        }
        return new RowInfo(severity, serviceType, summary, content);
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

    public record RowInfo(String severity, String serviceType, String summary, String content) {
    }
}
