package com.alertworker.gateway.service;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.gateway.dto.ParsedAlert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleMatcher {

    private final AlertRuleMapper alertRuleMapper;

    @SuppressWarnings("unchecked")
    public AlertRule match(ParsedAlert parsedAlert, AlertType alertType) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getAlertType, alertType)
               .eq(AlertRule::getDeleted, 0);

        List<AlertRule> rules = alertRuleMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(rules)) {
            log.warn("No alert rules found for type: {}", alertType);
            return null;
        }

        for (AlertRule rule : rules) {
            String matchKey = rule.getMatchKey();
            String actualValue = null;

            if (alertType == AlertType.PROMETHEUS) {
                actualValue = extractPrometheusLabel(parsedAlert.getContext(), matchKey);
            } else {
                Object val = parsedAlert.getContext().get(matchKey);
                actualValue = val != null ? val.toString() : null;
            }

            if (actualValue == null) {
                continue;
            }

            if (!StringUtils.hasText(rule.getMatchValue())) {
                return rule;
            }

            if (rule.getMatchValue().equals(actualValue)) {
                return rule;
            }
        }

        log.warn("No matching alert rule found for alert type: {}, service_type: {}", alertType, parsedAlert.getServiceType());
        return null;
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
}
