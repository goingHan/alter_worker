package com.alertworker.gateway.controller;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.gateway.dto.ParsedAlert;
import com.alertworker.gateway.parser.JsonAlertParser;
import com.alertworker.gateway.parser.PrometheusAlertParser;
import com.alertworker.gateway.rabbitmq.AlertMessageProducer;
import com.alertworker.gateway.service.AlertRuleMatcher;
import com.alertworker.gateway.service.GatewayAlertMessageService;
import com.alertworker.gateway.service.MessageBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final PrometheusAlertParser prometheusAlertParser;
    private final JsonAlertParser jsonAlertParser;
    private final AlertRuleMatcher alertRuleMatcher;
    private final MessageBuilder messageBuilder;
    private final AlertMessageProducer alertMessageProducer;
    private final GatewayAlertMessageService gatewayAlertMessageService;

    @PostMapping("/alerts/prometheus")
    public ResponseEntity<?> receivePrometheusAlert(HttpServletRequest request) {
        return handleAlert(request, AlertType.PROMETHEUS);
    }

    @PostMapping("/alerts/json")
    public ResponseEntity<?> receiveJsonAlert(HttpServletRequest request) {
        return handleAlert(request, AlertType.JSON);
    }

    private ResponseEntity<?> handleAlert(HttpServletRequest request, AlertType alertType) {
        Long apiKeyId = (Long) request.getAttribute("apiKeyId");
        String payload = readBody(request);

        try {
            ParsedAlert parsedAlert = alertType == AlertType.PROMETHEUS
                    ? prometheusAlertParser.parse(payload)
                    : jsonAlertParser.parse(payload);

            AlertRule rule = alertRuleMatcher.match(parsedAlert, alertType);
            if (rule == null) {
                log.warn("No matching alert rule found for {} alert", alertType);
                AlertMessage msg = gatewayAlertMessageService.create(apiKeyId, null, parsedAlert.getContext());
                gatewayAlertMessageService.transitionToFailed(msg.getId(), MessageSubStatus.gw_failed, "No matching alert rule");
                return ResponseEntity.badRequest().body(Map.of("error", "No matching alert rule"));
            }

            AlertMessage message = gatewayAlertMessageService.create(apiKeyId, rule.getId(), parsedAlert.getContext());
            AlertMessagePayload mqPayload = messageBuilder.build(rule, parsedAlert);
            mqPayload.setAlertMessageId(message.getId());

            alertMessageProducer.send(mqPayload);
            gatewayAlertMessageService.transitionToGwSent(message.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("messageId", message.getId());
            result.put("status", "GW_SENT");
            result.put("recipientCount", mqPayload.getRecipients().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to handle {} alert", alertType, e);
            AlertMessage msg = gatewayAlertMessageService.create(apiKeyId, null, Map.of("rawPayload", payload, "error", e.getMessage()));
            gatewayAlertMessageService.transitionToFailed(msg.getId(), MessageSubStatus.gw_failed, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            log.error("Failed to read request body", e);
            throw new RuntimeException("Failed to read request body", e);
        }
        return sb.toString();
    }
}
