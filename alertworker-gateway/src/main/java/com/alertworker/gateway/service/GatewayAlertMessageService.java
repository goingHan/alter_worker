package com.alertworker.gateway.service;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.service.AuditLogService;
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
public class GatewayAlertMessageService {

    private final AlertMessageMapper alertMessageMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AlertMessage create(Long apiKeyId, Long alertRuleId, Map<String, Object> context) {
        try {
            String payload = objectMapper.writeValueAsString(context);
            AlertMessage message = new AlertMessage();
            message.setApiKeyId(apiKeyId);
            message.setAlertRuleId(alertRuleId);
            message.setPayload(payload);
            message.setStatus(MessageStatus.recieved);
            message.setRetryCount(0);
            alertMessageMapper.insert(message);
            return message;
        } catch (Exception e) {
            log.error("Failed to create alert message", e);
            throw new RuntimeException("Failed to persist alert message", e);
        }
    }

    public void transitionToGwSent(Long messageId) {
        updateStatus(messageId, MessageStatus.GW_SENT, null);
        auditLogService.logEvent("GW_SENT", "Message " + messageId + " sent to RabbitMQ");
    }

    public void transitionToFailed(Long messageId, MessageSubStatus subStatus, String reason) {
        updateStatus(messageId, MessageStatus.FAILED, subStatus);
        auditLogService.logEvent("GW_FAILED", "Message " + messageId + " failed: " + reason);
    }

    private void updateStatus(Long messageId, MessageStatus status, MessageSubStatus subStatus) {
        LambdaUpdateWrapper<AlertMessage> wrapper = new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getStatus, status)
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now());
        if (subStatus != null) {
            wrapper.set(AlertMessage::getSubStatus, subStatus);
        } else {
            wrapper.setSql("sub_status = NULL");
        }
        alertMessageMapper.update(null, wrapper);
    }
}
