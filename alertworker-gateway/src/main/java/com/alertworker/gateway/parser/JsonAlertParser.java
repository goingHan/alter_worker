package com.alertworker.gateway.parser;

import com.alertworker.gateway.dto.ParsedAlert;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonAlertParser {

    private final ObjectMapper objectMapper;

    public ParsedAlert parse(String payload) {
        try {
            Map<String, Object> context = objectMapper.readValue(payload, new TypeReference<>() {});
            Object serviceTypeObj = context.get("service_type");
            String serviceType = serviceTypeObj != null ? serviceTypeObj.toString() : null;

            return ParsedAlert.builder()
                    .rawPayload(payload)
                    .context(context)
                    .serviceType(serviceType)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON alert payload", e);
            throw new IllegalArgumentException("Invalid JSON alert payload", e);
        }
    }
}
