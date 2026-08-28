package com.alertworker.gateway.parser;

import com.alertworker.gateway.dto.ParsedAlert;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusAlertParser {

    private final ObjectMapper objectMapper;

    public ParsedAlert parse(String payload) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload, new TypeReference<>() {});
            Map<String, Object> context = new HashMap<>(root);

            String serviceType = null;
            Object alertsObj = root.get("alerts");
            if (alertsObj instanceof List<?> alerts && !alerts.isEmpty()) {
                Object first = alerts.get(0);
                if (first instanceof Map<?, ?> alertMap) {
                    Object labelsObj = alertMap.get("labels");
                    if (labelsObj instanceof Map<?, ?> labels) {
                        Object st = labels.get("service_type");
                        if (st != null) {
                            serviceType = st.toString();
                        }
                        context.put("labels", labels);
                    }
                    Object annotationsObj = alertMap.get("annotations");
                    if (annotationsObj instanceof Map<?, ?> annotations) {
                        context.put("annotations", annotations);
                    }
                }
            }

            return ParsedAlert.builder()
                    .rawPayload(payload)
                    .context(context)
                    .serviceType(serviceType)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Prometheus alert payload", e);
            throw new IllegalArgumentException("Invalid Prometheus alert payload", e);
        }
    }
}
