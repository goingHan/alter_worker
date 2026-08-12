package com.alertworker.worker.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.worker.channel.MessageChannelDispatcher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: extract core processing from AlertMessageConsumer so MQ and HTTP debug paths share one implementation
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMessageProcessor {

    private final WorkerAlertMessageService workerAlertMessageService;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRuleTemplateMapper alertRuleTemplateMapper;
    private final MessageTemplateMapper messageTemplateMapper;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final MessageChannelDispatcher messageChannelDispatcher;

    public ProcessingResult process(AlertMessagePayload payload) {
        Long messageId = payload.getAlertMessageId();
        workerAlertMessageService.transitionToDelivered(messageId);

        AlertRule rule = null;
        int successCount = 0;
        try {
            rule = alertRuleMapper.selectById(payload.getAlertRuleId());
            if (rule == null || rule.getDeleted() == 1) {
                throw new IllegalStateException("Alert rule not found or deleted: " + payload.getAlertRuleId());
            }

            Map<ChannelType, MessageTemplate> templateMap = loadTemplates(rule.getId());
            MessageTemplate defaultTemplate = messageTemplateMapper.selectOne(
                    new LambdaQueryWrapper<MessageTemplate>()
                            .eq(MessageTemplate::getIsDefault, true)
                            .eq(MessageTemplate::getDeleted, 0)
            );

            boolean anySuccess = false;
            List<AlertRecipient> recipients = payload.getRecipients();
            for (AlertRecipient recipient : recipients) {
                MessageTemplate template = templateMap.get(recipient.getChannelType());
                if (template == null) {
                    template = defaultTemplate;
                }
                if (template == null) {
                    log.error("No template found for channelType={} and no default template", recipient.getChannelType());
                    continue;
                }

                Map<String, Object> ctx = new HashMap<>(payload.getContext());
                ctx.put("recipient", recipient);
                String rendered = jinjavaTemplateService.render(template.getContent(), ctx);

                workerAlertMessageService.transitionToWorkerSent(messageId);
                boolean sent = messageChannelDispatcher.dispatch(recipient, rendered);
                if (sent) {
                    anySuccess = true;
                    successCount++;
                }
            }

            if (anySuccess || recipients.isEmpty()) {
                workerAlertMessageService.transitionToSuccess(messageId);
                return ProcessingResult.ok(successCount, recipients.size());
            }
            throw new RuntimeException("All channel dispatches failed");
        } catch (Exception e) {
            return handleFailure(messageId, e, rule, successCount,
                    payload.getRecipients() == null ? 0 : payload.getRecipients().size());
        }
    }

    private Map<ChannelType, MessageTemplate> loadTemplates(Long ruleId) {
        Map<ChannelType, MessageTemplate> map = new HashMap<>();
        List<AlertRuleTemplate> ruleTemplates = alertRuleTemplateMapper.selectList(
                new LambdaQueryWrapper<AlertRuleTemplate>().eq(AlertRuleTemplate::getRuleId, ruleId)
        );
        for (AlertRuleTemplate rt : ruleTemplates) {
            MessageTemplate template = messageTemplateMapper.selectById(rt.getTemplateId());
            if (template != null && template.getDeleted() == 0) {
                map.put(rt.getChannelType(), template);
            }
        }
        return map;
    }

    private ProcessingResult handleFailure(Long messageId, Exception e, AlertRule rule,
                                           int successCount, int recipientCount) {
        log.error("Failed to process message {}, error: {}", messageId, e.getMessage(), e);
        int retryCount = workerAlertMessageService.getRetryCount(messageId);
        int maxRetry = (rule != null && rule.getRetryMax() != null) ? rule.getRetryMax() : 3;
        String backoffStr = (rule != null && rule.getRetryBackoff() != null) ? rule.getRetryBackoff() : "5s,10s,20s";

        workerAlertMessageService.incrementRetryCount(messageId);
        workerAlertMessageService.transitionToFailed(messageId, MessageSubStatus.worker_failed, e.getMessage());

        if (retryCount < maxRetry) {
            long delayMs = calculateBackoffDelay(retryCount, backoffStr);
            log.warn("Retrying message {} in {}ms, retryCount={}/{}", messageId, delayMs, retryCount + 1, maxRetry);
            return ProcessingResult.failed(successCount, recipientCount, true, delayMs, e.getMessage());
        }
        log.error("Message {} exhausted retries, sending to dead letter", messageId);
        workerAlertMessageService.writeToDeadLetter(messageId, e.getMessage());
        return ProcessingResult.failed(successCount, recipientCount, false, 0L, e.getMessage());
    }

    private long calculateBackoffDelay(int retryCount, String backoffStr) {
        String[] parts = backoffStr.split(",");
        if (retryCount < parts.length) {
            String unit = parts[retryCount].trim();
            if (unit.endsWith("s")) {
                return Long.parseLong(unit.substring(0, unit.length() - 1)) * 1000;
            }
        }
        return 5000;
    }
}
