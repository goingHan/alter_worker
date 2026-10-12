package com.alertworker.common.service;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMessageRecordService {

    private final AlertMessageMapper alertMessageMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AlertMessage create(Long apiKeyId, Long alertRuleId, Map<String, Object> context,
                               String messageNo, String rawPayload, String channelNames) {
        try {
            String payload = objectMapper.writeValueAsString(context);
            AlertMessage message = new AlertMessage();
            message.setMessageNo(messageNo);
            message.setApiKeyId(apiKeyId);
            message.setAlertRuleId(alertRuleId);
            message.setPayload(payload);
            message.setRawPayload(rawPayload);
            message.setChannelNames(channelNames);
            message.setStatus(MessageStatus.recieved);
            message.setRetryCount(0);
            message.setReceivedAt(LocalDateTime.now());
            alertMessageMapper.insert(message);
            return message;
        } catch (Exception e) {
            log.error("Failed to create alert message", e);
            throw new RuntimeException("Failed to persist alert message", e);
        }
    }

    public void transitionToGwSent(Long messageId) {
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getStatus, MessageStatus.GW_SENT)
                .set(AlertMessage::getGwSentAt, LocalDateTime.now())
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now())
                .setSql("sub_status = NULL"));
        auditLogService.logEvent("GW_SENT", "Message " + messageId + " sent to RabbitMQ");
    }

    public void transitionToFailed(Long messageId, MessageSubStatus subStatus, String reason) {
        updateStatus(messageId, MessageStatus.FAILED, subStatus);
        auditLogService.logEvent("GW_FAILED", "Message " + messageId + " failed: " + reason);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: terminal state for a silenced rule — the alert is dropped
    // before channel resolution and never reaches RabbitMQ, but the record stays queryable for the dashboard
    public void transitionToSilenced(Long messageId, Long alertRuleId) {
        updateStatus(messageId, MessageStatus.SILENCED, null);
        auditLogService.logEvent("GW_SILENCED",
                "Message " + messageId + " dropped by silenced alert rule " + alertRuleId);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: terminal state for an alert that matched no rule. It is dropped
    // like a silenced alert (no recipients, no MQ) but tracked under its own status so the dashboard can show what
    // arrived without a matching rule instead of burying it among real failures.
    public void transitionToUnmatched(Long messageId, String reason) {
        updateStatus(messageId, MessageStatus.UNMATCHED, null);
        auditLogService.logEvent("GW_UNMATCHED",
                "Message " + messageId + " dropped, matched no alert rule: " + reason);
    }

    // report-only rule: the alert is stored without enqueueing so the daily-report job can aggregate it later
    public void transitionToReportPending(Long messageId, Long alertRuleId) {
        updateStatus(messageId, MessageStatus.REPORT_PENDING, null);
        auditLogService.logEvent("GW_REPORT_PENDING",
                "Message " + messageId + " stored as report-only pending for alert rule " + alertRuleId);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: WARNING-severity alert parked while the warning digest is on —
    // the periodic digest job aggregates it into one hosted HTML page instead of delivering it immediately
    public void transitionToWarningPending(Long messageId, Long alertRuleId) {
        updateStatus(messageId, MessageStatus.WARNING_PENDING, null);
        auditLogService.logEvent("GW_WARNING_PENDING",
                "Message " + messageId + " stored as warning-digest pending for alert rule " + alertRuleId);
    }

    private void updateStatus(Long messageId, MessageStatus status, MessageSubStatus subStatus) {
        LambdaUpdateWrapper<AlertMessage> wrapper = new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getStatus, status)
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now())
                .set(AlertMessage::getFinishedAt, LocalDateTime.now());
        if (subStatus != null) {
            wrapper.set(AlertMessage::getSubStatus, subStatus);
        } else {
            wrapper.setSql("sub_status = NULL");
        }
        alertMessageMapper.update(null, wrapper);
    }
}
