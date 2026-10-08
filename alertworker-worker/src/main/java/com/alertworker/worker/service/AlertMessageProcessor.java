package com.alertworker.worker.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.worker.channel.MessageChannelDispatcher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final MessageChannelMapper messageChannelMapper;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final MessageChannelDispatcher messageChannelDispatcher;
    private final ObjectMapper objectMapper;

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

            List<AlertRecipient> recipients = payload.getRecipients();
            Map<ChannelType, MessageTemplate> templateMap = loadTemplates(rule.getId());
            MessageTemplate defaultTemplate = messageTemplateMapper.selectOne(
                    new LambdaQueryWrapper<MessageTemplate>()
                            .eq(MessageTemplate::getIsDefault, true)
                            .eq(MessageTemplate::getDeleted, 0)
            );
            Map<Long, MessageTemplate> channelDefaults = loadChannelDefaults(recipients);

            boolean anySuccess = false;
            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260907: group recipients by message channel so all of them are
            // notified in one message (and @-ed together in DingDing) instead of one message per recipient.
            Map<Long, List<AlertRecipient>> channels = new LinkedHashMap<>();
            for (AlertRecipient recipient : recipients) {
                channels.computeIfAbsent(recipient.getMessageChannelId(), k -> new ArrayList<>()).add(recipient);
            }

            for (Map.Entry<Long, List<AlertRecipient>> entry : channels.entrySet()) {
                List<AlertRecipient> group = entry.getValue();
                AlertRecipient representative = group.get(0);
                MessageTemplate template = templateMap.get(representative.getChannelType());
                if (template == null) {
                    template = channelDefaults.get(entry.getKey());
                }
                if (template == null) {
                    template = defaultTemplate;
                }
                if (template == null) {
                    log.error("No template found for channelType={} and no default template", representative.getChannelType());
                    continue;
                }

                Map<String, Object> ctx = new HashMap<>(payload.getContext());
                ctx.put("recipient", representative);
                ctx.put("messageId", payload.getMessageNo() != null ? payload.getMessageNo() : String.valueOf(messageId));
                String rendered = jinjavaTemplateService.render(template.getContent(), ctx);
                boolean markdownStyle = representative.getChannelType() == ChannelType.DINGDING
                        && isMarkdownChannel(entry.getKey());
                rendered = appendTraceFooter(rendered, payload, messageId, representative, markdownStyle);

                workerAlertMessageService.transitionToWorkerSent(messageId);
                boolean sent = messageChannelDispatcher.dispatch(group, rendered);
                if (sent) {
                    anySuccess = true;
                    successCount += group.size();
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

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260901: channel-level default template fallback
    private Map<Long, MessageTemplate> loadChannelDefaults(List<AlertRecipient> recipients) {
        Map<Long, MessageTemplate> map = new HashMap<>();
        for (AlertRecipient recipient : recipients) {
            if (recipient.getMessageChannelId() == null) {
                continue;
            }
            MessageChannel channel = messageChannelMapper.selectById(recipient.getMessageChannelId());
            if (channel == null || channel.getDefaultTemplateId() == null) {
                continue;
            }
            MessageTemplate template = messageTemplateMapper.selectById(channel.getDefaultTemplateId());
            if (template != null && template.getDeleted() == 0) {
                map.put(channel.getId(), template);
            }
        }
        return map;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260908: render the Alert ID footer as an h6 heading on markdown channels,
    // matching the keyword line so both meta lines use the smallest DingDing markdown font
    private boolean isMarkdownChannel(Long channelId) {
        if (channelId == null) {
            return false;
        }
        MessageChannel channel = messageChannelMapper.selectById(channelId);
        if (channel == null || channel.getConfigJson() == null) {
            return false;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfigJson(), Map.class);
            Object msgType = config.get("msgType");
            // actionCard text also renders markdown, so it gets the same h6 footer style
            return msgType != null
                    && ("markdown".equals(msgType.toString()) || "action_card".equals(msgType.toString()));
        } catch (Exception e) {
            return false;
        }
    }

    private String appendTraceFooter(String rendered, AlertMessagePayload payload, Long messageId,
                                     AlertRecipient recipient, boolean markdownStyle) {
        String messageNo = payload.getMessageNo() != null ? payload.getMessageNo() : String.valueOf(messageId);
        String sendTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String footer;
        if (recipient.getChannelType() == ChannelType.EMAIL
                && rendered.trim().startsWith("<") && rendered.contains("</")) {
            footer = "<hr/><p style=\"color:#888;font-size:12px;\">Alert ID: " + messageNo
                    + " | 发送时间: " + sendTime + "</p>";
        } else if (markdownStyle) {
            footer = "\n\n---\n###### Alert ID: " + messageNo + " | 发送时间: " + sendTime;
        } else {
            footer = "\n\n---\nAlert ID: " + messageNo + " | 发送时间: " + sendTime;
        }
        return rendered + footer;
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
