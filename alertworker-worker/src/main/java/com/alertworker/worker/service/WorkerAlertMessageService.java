package com.alertworker.worker.service;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.DeadLetterMessage;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.DeadLetterMessageMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerAlertMessageService {

    private final AlertMessageMapper alertMessageMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;
    private final AuditLogService auditLogService;

    @Transactional
    public void transitionToDelivered(Long messageId) {
        updateStatus(messageId, MessageStatus.DELIVERED, null);
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getWorkerReceivedAt, LocalDateTime.now())
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now()));
    }

    @Transactional
    public void transitionToWorkerSent(Long messageId) {
        updateStatus(messageId, MessageStatus.WORKER_SENT, null);
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getWorkerSentAt, LocalDateTime.now())
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now()));
    }

    @Transactional
    public void transitionToSuccess(Long messageId) {
        updateStatus(messageId, MessageStatus.success, null);
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getFinishedAt, LocalDateTime.now())
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now()));
        auditLogService.logEvent("MESSAGE_SUCCESS", "Message " + messageId + " delivered successfully");
    }

    @Transactional
    public void transitionToFailed(Long messageId, MessageSubStatus subStatus, String reason) {
        updateStatus(messageId, MessageStatus.FAILED, subStatus);
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .set(AlertMessage::getFinishedAt, LocalDateTime.now())
                .set(AlertMessage::getUpdatedAt, LocalDateTime.now()));
        auditLogService.logEvent("WORKER_FAILED", "Message " + messageId + " failed: " + reason);
    }

    public void incrementRetryCount(Long messageId) {
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, messageId)
                .setSql("retry_count = COALESCE(retry_count, 0) + 1"));
    }

    public int getRetryCount(Long messageId) {
        AlertMessage message = alertMessageMapper.selectById(messageId);
        return message != null && message.getRetryCount() != null ? message.getRetryCount() : 0;
    }

    @Transactional
    public void writeToDeadLetter(Long messageId, String errorInfo) {
        DeadLetterMessage dlq = new DeadLetterMessage();
        dlq.setAlertMessageId(messageId);
        dlq.setErrorInfo(errorInfo);
        dlq.setCreatedAt(LocalDateTime.now());
        deadLetterMessageMapper.insert(dlq);
        log.info("Message {} written to dead letter queue", messageId);
    }

    private void updateStatus(Long messageId, MessageStatus status, MessageSubStatus subStatus) {
        AlertMessage message = alertMessageMapper.selectById(messageId);
        if (message != null) {
            message.setStatus(status);
            message.setSubStatus(subStatus);
            alertMessageMapper.updateById(message);
        }
    }
}
