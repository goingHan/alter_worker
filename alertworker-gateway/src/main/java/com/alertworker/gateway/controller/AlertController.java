package com.alertworker.gateway.controller;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.DeadLetterMessage;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.DeadLetterMessageMapper;
import com.alertworker.common.util.MessageNoGenerator;
import com.alertworker.gateway.service.AlertIngestService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private final AlertIngestService alertIngestService;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertMessageMapper alertMessageMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;

    @PostMapping("/alerts/prometheus")
    public ResponseEntity<?> receivePrometheusAlert(HttpServletRequest request) {
        return handleAlert(request, AlertType.PROMETHEUS);
    }

    @PostMapping("/alerts/json")
    public ResponseEntity<?> receiveJsonAlert(HttpServletRequest request) {
        return handleAlert(request, AlertType.JSON);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260901: retry a dead-letter message by re-running the ingest pipeline on the stored raw payload
    @PostMapping("/alerts/dead-letters/{id}/retry")
    public ResponseEntity<?> retryDeadLetter(@PathVariable Long id) {
        DeadLetterMessage deadLetter = deadLetterMessageMapper.selectById(id);
        if (deadLetter == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Dead letter not found"));
        }
        AlertMessage original = alertMessageMapper.selectById(deadLetter.getAlertMessageId());
        if (original == null || original.getRawPayload() == null || original.getRawPayload().isBlank()) {
            return ResponseEntity.status(404).body(Map.of("error", "Original alert or raw payload not found"));
        }
        AlertType alertType = resolveAlertType(original.getAlertRuleId(), original.getRawPayload());
        ResponseEntity<?> response = toResponse(alertIngestService.ingest(
                original.getApiKeyId(), alertType, original.getRawPayload(), MessageNoGenerator.generate(), null, null));
        if (response.getStatusCode().is2xxSuccessful()) {
            deadLetterMessageMapper.deleteById(id);
            log.info("Dead letter {} retried successfully", id);
        }
        return response;
    }

    @PostMapping("/alerts/dead-letters/retry-by-message/{alertMessageId}")
    public ResponseEntity<?> retryDeadLetterByMessage(@PathVariable Long alertMessageId) {
        DeadLetterMessage deadLetter = deadLetterMessageMapper.selectOne(
                new LambdaQueryWrapper<DeadLetterMessage>()
                        .eq(DeadLetterMessage::getAlertMessageId, alertMessageId)
                        .last("LIMIT 1")
        );
        if (deadLetter == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Dead letter not found"));
        }
        return retryDeadLetter(deadLetter.getId());
    }

    private AlertType resolveAlertType(Long alertRuleId, String rawPayload) {
        if (alertRuleId != null) {
            AlertRule rule = alertRuleMapper.selectById(alertRuleId);
            if (rule != null && rule.getAlertType() != null) {
                return rule.getAlertType();
            }
        }
        return (rawPayload != null && rawPayload.contains("labels")) ? AlertType.PROMETHEUS : AlertType.JSON;
    }

    private ResponseEntity<?> handleAlert(HttpServletRequest request, AlertType alertType) {
        Long apiKeyId = (Long) request.getAttribute("apiKeyId");
        String rawPayload = readBody(request);
        return toResponse(alertIngestService.ingest(
                apiKeyId, alertType, rawPayload, MessageNoGenerator.generate(), null, null));
    }

    private ResponseEntity<?> toResponse(AlertIngestService.IngestResult result) {
        if (result.success()) {
            Map<String, Object> body = new HashMap<>();
            body.put("messageId", result.messageNo());
            body.put("recordId", result.recordId());
            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: a dropped alert (silenced rule, or no matching rule at
            // all) answers 2xx because it was accepted and handled, but never reaches a channel — so the status must
            // say which drop happened instead of the usual GW_SENT
            String status = result.unmatched() ? "UNMATCHED" : result.silenced() ? "SILENCED" : "GW_SENT";
            body.put("status", status);
            body.put("silenced", result.silenced());
            body.put("unmatched", result.unmatched());
            body.put("recipientCount", result.recipientCount());
            if (result.unmatched()) {
                body.put("reason", result.error());
            }
            return ResponseEntity.ok(body);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("error", result.error());
        body.put("messageId", result.messageNo());
        return ResponseEntity.status(result.status()).body(body);
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
