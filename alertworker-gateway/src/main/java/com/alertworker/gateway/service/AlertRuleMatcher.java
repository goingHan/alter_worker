package com.alertworker.gateway.service;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.gateway.dto.ParsedAlert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleMatcher {

    private final AlertRuleMapper alertRuleMapper;
    private final ObjectMapper objectMapper;

    public AlertRule match(ParsedAlert parsedAlert, AlertType alertType) {
        List<AlertRule> rules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>()
                        .eq(AlertRule::getAlertType, alertType)
                        .eq(AlertRule::getDeleted, 0));
        if (CollectionUtils.isEmpty(rules)) {
            log.warn("No alert rules found for type: {}", alertType);
            return null;
        }

        for (AlertRule rule : rules) {
            if (matches(parsedAlert, alertType, rule, resolveConditions(rule))) {
                return rule;
            }
        }

        log.warn("No matching alert rule found for alert type: {}, service_type: {}",
                alertType, parsedAlert.getServiceType());
        return null;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: per-condition operators (AND/OR/NOT, left to right); NOT means "AND NOT".
    // Legacy rules without "op" fall back to global match_logic + the per-condition "not" flag.
    // Empty condition value = "label exists"; a negated condition also matches when the label is absent.
    private boolean matches(ParsedAlert parsedAlert, AlertType alertType, AlertRule rule, ResolvedConditions resolved) {
        List<MatchCondition> conditions = resolved.conditions();
        if (CollectionUtils.isEmpty(conditions)) {
            return false;
        }
        if (resolved.perConditionOps()) {
            boolean result = hit(parsedAlert, alertType, conditions.get(0));
            if (conditions.get(0).not()) {
                result = !result;
            }
            for (int i = 1; i < conditions.size(); i++) {
                MatchCondition condition = conditions.get(i);
                boolean current = hit(parsedAlert, alertType, condition);
                String op = condition.op() == null ? "AND" : condition.op().toUpperCase();
                switch (op) {
                    case "OR" -> result = result || current;
                    case "NOT" -> result = result && !current;
                    default -> result = result && current;
                }
            }
            return result;
        }
        boolean and = "AND".equalsIgnoreCase(rule.getMatchLogic());
        for (MatchCondition condition : conditions) {
            boolean current = hit(parsedAlert, alertType, condition);
            if (condition.not()) {
                current = !current;
            }
            if (and) {
                if (!current) {
                    return false;
                }
            } else if (current) {
                return true;
            }
        }
        return and;
    }

    private boolean hit(ParsedAlert parsedAlert, AlertType alertType, MatchCondition condition) {
        String actualValue = extractValue(parsedAlert, alertType, condition.key());
        return StringUtils.hasText(condition.value())
                ? condition.value().equals(actualValue)
                : actualValue != null;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260901: support multiple match conditions (JSON), fallback to legacy single key/value
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: read per-condition "op" (AND/OR/NOT); legacy "not" is normalised to op=NOT
    private ResolvedConditions resolveConditions(AlertRule rule) {
        List<MatchCondition> conditions = new ArrayList<>();
        boolean perConditionOps = false;
        if (StringUtils.hasText(rule.getMatchConditions())) {
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        rule.getMatchConditions(), new TypeReference<>() {});
                for (Map<String, Object> m : parsed) {
                    Object rawOp = m.get("op");
                    String op = rawOp == null ? null : rawOp.toString();
                    if (StringUtils.hasText(op)) {
                        perConditionOps = true;
                    } else if (isTrue(m.get("not"))) {
                        op = "NOT";
                    }
                    conditions.add(new MatchCondition(
                            m.get("key") == null ? null : m.get("key").toString(),
                            m.get("value") == null ? null : m.get("value").toString(),
                            op));
                }
            } catch (Exception e) {
                log.warn("Failed to parse match_conditions for rule {}: {}", rule.getId(), e.getMessage());
            }
        }
        if (conditions.isEmpty()) {
            conditions.add(new MatchCondition(rule.getMatchKey(), rule.getMatchValue(), null));
        }
        return new ResolvedConditions(conditions, perConditionOps);
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String extractValue(ParsedAlert parsedAlert, AlertType alertType, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (alertType == AlertType.PROMETHEUS) {
            return extractPrometheusLabel(parsedAlert.getContext(), key);
        }
        Object val = parsedAlert.getContext().get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String extractPrometheusLabel(Map<String, Object> context, String labelKey) {
        Object alertsObj = context.get("alerts");
        if (!(alertsObj instanceof List<?> alerts) || alerts.isEmpty()) {
            return null;
        }
        Object first = alerts.get(0);
        if (!(first instanceof Map<?, ?> alertMap)) {
            return null;
        }
        Object labelsObj = alertMap.get("labels");
        if (!(labelsObj instanceof Map<?, ?> labels)) {
            return null;
        }
        Object val = labels.get(labelKey);
        return val != null ? val.toString() : null;
    }

    private record MatchCondition(String key, String value, String op) {
        private boolean not() {
            return "NOT".equalsIgnoreCase(op);
        }
    }

    private record ResolvedConditions(List<MatchCondition> conditions, boolean perConditionOps) {
    }
}
