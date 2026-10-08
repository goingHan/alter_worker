package com.alertworker.worker.controller;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.worker.service.AlertMessageProcessor;
import com.alertworker.worker.service.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: HTTP debug endpoint to run the worker pipeline directly, bypassing RabbitMQ
@Slf4j
@RestController
@RequestMapping("/api/v1/worker")
@RequiredArgsConstructor
public class DebugDispatchController {

    private final AlertMessageProcessor alertMessageProcessor;

    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatch(@RequestBody(required = false) AlertMessagePayload payload) {
        if (payload == null) {
            return error(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (payload.getAlertMessageId() == null) {
            return error(HttpStatus.BAD_REQUEST, "alertMessageId is required");
        }
        if (payload.getAlertRuleId() == null) {
            return error(HttpStatus.BAD_REQUEST, "alertRuleId is required");
        }
        if (payload.getRecipients() == null) {
            return error(HttpStatus.BAD_REQUEST, "recipients must not be null (use [] for empty)");
        }

        ProcessingResult result = alertMessageProcessor.process(payload);
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", payload.getAlertMessageId());
        body.put("result", result.success() ? "success" : "failed");
        body.put("successCount", result.successCount());
        body.put("recipientCount", result.recipientCount());
        if (result.error() != null) {
            body.put("error", result.error());
        }
        return result.success()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
