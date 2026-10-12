package com.alertworker.processor.scheduler;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.MessageKind;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.TemplateFormat;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.messaging.AlertMessageProducer;
import com.alertworker.common.scheduler.JobExecutor;
import com.alertworker.common.service.AlertMessageRecordService;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.common.service.RuleRecipientResolver;
import com.alertworker.common.storage.ObjectStorageService;
import com.alertworker.common.storage.StorageProperties;
import com.alertworker.common.util.MessageNoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WARNING digest job ({@code jobType = WARNING_DIGEST}).
 *
 * <p>While the digest is enabled, WARNING-severity alerts are parked at the gateway as {@code WARNING_PENDING} instead
 * of being delivered one-by-one. This job runs at the configured cron, rolls every not-yet-reported pending row into
 * one self-contained HTML page (with a client-side keyword filter), uploads it to object storage, and sends it through
 * the normal REPORT path (DingDing gets the link, email embeds the HTML inline).
 *
 * <p><b>Recipients</b> are not configured here: they are the merged, de-duplicated recipients of the rules that
 * produced the pending alerts (each rule's groups/assignees/channel bindings, filtered per service type). A DingDing
 * placeholder (no @) is added for bound DingDing channels that resolve no assignee, so group robots still receive it.
 *
 * <p><b>Incremental semantics</b>: only rows with {@code digest_message_id IS NULL} are aggregated, and they are
 * claimed (stamped with the digest message id) after the message is enqueued — a later run reports only new warnings.
 * On permanent delivery failure the Worker clears the claim so the rows re-enter the next digest.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WarningDigestJobExecutor implements JobExecutor {

    public static final String JOB_TYPE = "WARNING_DIGEST";
    public static final String TEMPLATE_NAME = "内置-WARNING汇总模板";
    private static final String MESSAGE_NO_PREFIX = "DIGEST-";
    private static final String TITLE = "告警汇总";
    private static final String MESSAGE_TYPE_ALERT = "ALERT";
    private static final String MESSAGE_TYPE_DIGEST = "WARNING_DIGEST";
    private static final String PLACEHOLDER_NAME = "WARNING汇总推送";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DETAIL_ROWS = 500;

    private final ObjectMapper objectMapper;
    private final AlertMessageMapper alertMessageMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final MessageTemplateMapper messageTemplateMapper;
    private final MessageChannelMapper messageChannelMapper;
    private final AlertMessageRecordService alertMessageRecordService;
    private final AlertMessageProducer alertMessageProducer;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final RuleRecipientResolver ruleRecipientResolver;
    private final AlertRowExtractor alertRowExtractor;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void execute(ScheduledJob job) {
        run(job);
    }

    /** Manual/periodic entry point returning what happened so "立即生成" can explain a no-op. */
    public DigestRunResult run(ScheduledJob job) {
        Long templateId = parseTemplateId(job);
        List<AlertMessage> rows = alertMessageMapper.selectList(new LambdaQueryWrapper<AlertMessage>()
                .eq(AlertMessage::getStatus, MessageStatus.WARNING_PENDING)
                .eq(AlertMessage::getMessageType, MESSAGE_TYPE_ALERT)
                .isNull(AlertMessage::getDigestMessageId)
                .orderByAsc(AlertMessage::getCreatedAt));
        if (rows.isEmpty()) {
            log.info("Warning digest job id={} has no pending warnings", job.getId());
            return DigestRunResult.notSent("no-data");
        }

        Map<Long, AlertRule> rulesById = loadRules(rows);
        List<AlertRecipient> recipients = resolveMergedRecipients(rows, rulesById);
        if (recipients.isEmpty()) {
            log.warn("Warning digest job id={} resolved no recipients for {} pending warning(s), nothing to send",
                    job.getId(), rows.size());
            return DigestRunResult.notSent("no-recipients");
        }

        MessageTemplate template = resolveTemplate(templateId);
        if (template == null) {
            log.error("Warning digest job id={} resolved no template, nothing to send", job.getId());
            return DigestRunResult.notSent("no-template");
        }

        String messageNo = MESSAGE_NO_PREFIX + MessageNoGenerator.generate();
        Map<String, Object> context = buildContext(rows, rulesById);
        String renderedContent = jinjavaTemplateService.render(template.getContent(), context);

        String objectKey = null;
        String contentUrl = null;
        if (template.getFormat() == TemplateFormat.HTML) {
            LocalDate today = LocalDate.now(ZONE);
            String relative = "digests/" + today.getYear()
                    + "/" + String.format("%02d", today.getMonthValue())
                    + "/" + String.format("%02d", today.getDayOfMonth())
                    + "/" + messageNo + ".html";
            objectKey = storageProperties.objectKey(relative);
            objectStorageService.put(objectKey, renderedContent.getBytes(StandardCharsets.UTF_8),
                    "text/html; charset=utf-8");
            contentUrl = objectStorageService.publicUrl(objectKey);
            log.info("Warning digest job id={} uploaded HTML digest to objectKey={} contentUrl={}",
                    job.getId(), objectKey, contentUrl);
        }

        String channelNames = resolveChannelNames(recipients);
        AlertMessage digestMessage = alertMessageRecordService.create(
                null, null, context, messageNo, null, channelNames);
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, digestMessage.getId())
                .set(AlertMessage::getMessageType, MESSAGE_TYPE_DIGEST)
                .set(AlertMessage::getContentUrl, contentUrl)
                .set(AlertMessage::getObjectKey, objectKey));
        alertMessageRecordService.transitionToGwSent(digestMessage.getId());

        AlertMessagePayload payload = AlertMessagePayload.builder()
                .kind(MessageKind.REPORT)
                .alertMessageId(digestMessage.getId())
                .messageNo(messageNo)
                .alertRuleId(null)
                .reportTemplateId(template.getId())
                .renderedContent(renderedContent)
                .htmlUrl(contentUrl)
                .context(context)
                .recipients(recipients)
                .build();
        alertMessageProducer.send(payload);

        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .in(AlertMessage::getId, rows.stream().map(AlertMessage::getId).toList())
                .set(AlertMessage::getDigestMessageId, digestMessage.getId()));
        log.info("Warning digest job id={} aggregated {} warning(s) into digest message no={} for {} recipient(s)",
                job.getId(), rows.size(), messageNo, recipients.size());
        return new DigestRunResult(true, null, rows.size(), recipients.size(), messageNo);
    }

    private Map<Long, AlertRule> loadRules(List<AlertMessage> rows) {
        Map<Long, AlertRule> rulesById = new HashMap<>();
        for (AlertMessage row : rows) {
            Long ruleId = row.getAlertRuleId();
            if (ruleId != null && !rulesById.containsKey(ruleId)) {
                AlertRule rule = alertRuleMapper.selectById(ruleId);
                if (rule != null) {
                    rulesById.put(ruleId, rule);
                }
            }
        }
        return rulesById;
    }

    private List<AlertRecipient> resolveMergedRecipients(List<AlertMessage> rows, Map<Long, AlertRule> rulesById) {
        Map<String, AlertRecipient> byKey = new LinkedHashMap<>();
        List<ChannelType> seenTypes = new ArrayList<>();
        for (AlertMessage row : rows) {
            AlertRule rule = row.getAlertRuleId() == null ? null : rulesById.get(row.getAlertRuleId());
            if (rule == null) {
                continue;
            }
            String serviceType = alertRowExtractor.extract(row, rule).serviceType();
            for (AlertRecipient recipient : ruleRecipientResolver.resolve(rule, serviceType)) {
                byKey.putIfAbsent(recipient.getAssigneeId() + ":" + recipient.getChannelType() + ":"
                        + recipient.getMessageChannelId(), recipient);
            }
        }

        // #DingDing group robots deliver to the group regardless of who is @-ed, so bound DingDing channels with no
        // resolved assignee still deserve the digest (send once, no @). Email still needs a configured address.
        boolean hasDingding = byKey.values().stream().anyMatch(r -> r.getChannelType() == ChannelType.DINGDING);
        if (!hasDingding) {
            Map<Long, AlertRule> distinctRules = new LinkedHashMap<>();
            for (AlertMessage row : rows) {
                if (row.getAlertRuleId() != null && rulesById.get(row.getAlertRuleId()) != null) {
                    distinctRules.putIfAbsent(row.getAlertRuleId(), rulesById.get(row.getAlertRuleId()));
                }
            }
            Map<Long, Boolean> dingdingChannels = new LinkedHashMap<>();
            for (AlertRule rule : distinctRules.values()) {
                for (Map.Entry<ChannelType, List<Long>> entry
                        : ruleRecipientResolver.resolveRuleChannelIds(rule.getId()).entrySet()) {
                    if (entry.getKey() == ChannelType.DINGDING) {
                        for (Long channelId : entry.getValue()) {
                            dingdingChannels.put(channelId, Boolean.TRUE);
                        }
                    }
                }
            }
            for (Long channelId : dingdingChannels.keySet()) {
                byKey.put("digest:DINGDING:" + channelId, AlertRecipient.builder()
                        .cnName(PLACEHOLDER_NAME)
                        .channelType(ChannelType.DINGDING)
                        .messageChannelId(channelId)
                        .build());
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private Map<String, Object> buildContext(List<AlertMessage> rows, Map<Long, AlertRule> rulesById) {
        Map<Long, Integer> ruleCounts = new LinkedHashMap<>();
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        Map<String, Integer> serviceTypeCounts = new LinkedHashMap<>();
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: per-responsible-person counts (how many alerts each assignee
        // receives). Recipient resolution is per rule + service_type, so cache it to avoid re-querying per row.
        Map<String, Integer> assigneeCounts = new LinkedHashMap<>();
        Map<String, List<String>> assigneeCache = new HashMap<>();
        List<Map<String, Object>> alerts = new ArrayList<>();

        int detailCap = Math.min(rows.size(), MAX_DETAIL_ROWS);
        for (int i = 0; i < rows.size(); i++) {
            AlertMessage row = rows.get(i);
            Long ruleId = row.getAlertRuleId() != null ? row.getAlertRuleId() : 0L;
            AlertRule rule = rulesById.get(ruleId);
            AlertRowExtractor.RowInfo info = alertRowExtractor.extract(row, rule);
            ruleCounts.merge(ruleId, 1, Integer::sum);
            severityCounts.merge(info.severity(), 1, Integer::sum);
            serviceTypeCounts.merge(info.serviceType(), 1, Integer::sum);
            List<String> assignees = resolveAssigneeNames(rule, info.serviceType(), assigneeCache);
            for (String assignee : assignees) {
                assigneeCounts.merge(assignee, 1, Integer::sum);
            }
            if (i < detailCap) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("messageNo", row.getMessageNo());
                detail.put("ruleName", ruleName(rule, ruleId));
                detail.put("severity", info.severity());
                detail.put("serviceType", info.serviceType());
                detail.put("createdAt", row.getCreatedAt() == null
                        ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(row.getCreatedAt()));
                detail.put("summary", info.summary());
                // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: the digest detail shows who owns the alert and its content
                detail.put("content", info.content());
                detail.put("assignees", String.join("、", assignees));
                alerts.add(detail);
            }
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("title", TITLE);
        context.put("date", LocalDate.now(ZONE).toString());
        context.put("generatedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now));
        context.put("totalCount", rows.size());
        context.put("byRule", ruleCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ruleId", entry.getKey());
                    item.put("ruleName", ruleName(rulesById.get(entry.getKey()), entry.getKey()));
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList());
        context.put("bySeverity", toCountList(severityCounts, "severity"));
        context.put("byServiceType", toCountList(serviceTypeCounts, "serviceType"));
        context.put("byAssignee", toCountList(assigneeCounts, "assignee"));
        context.put("alerts", alerts);
        return context;
    }

    /**
     * Distinct responsible-person (assignee) names for a rule at the given service type, resolved with the same
     * semantics the gateway uses to pick recipients. {@code cache} is keyed by {@code ruleId|serviceType} because the
     * resolution hits several tables and the digest commonly has many rows per rule.
     */
    private List<String> resolveAssigneeNames(AlertRule rule, String serviceType, Map<String, List<String>> cache) {
        if (rule == null || rule.getId() == null) {
            return List.of();
        }
        String key = rule.getId() + "|" + serviceType;
        return cache.computeIfAbsent(key, k -> {
            List<String> names = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (AlertRecipient recipient : ruleRecipientResolver.resolve(rule, serviceType)) {
                String dedupe = recipient.getAssigneeId() != null
                        ? String.valueOf(recipient.getAssigneeId())
                        : recipient.getCnName();
                if (dedupe == null || !seen.add(dedupe)) {
                    continue;
                }
                String name = recipient.getCnName() != null ? recipient.getCnName() : dedupe;
                names.add(name);
            }
            return names;
        });
    }

    private List<Map<String, Object>> toCountList(Map<String, Integer> counts, String key) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(key, entry.getKey());
            item.put("count", entry.getValue());
            list.add(item);
        }
        return list;
    }

    private String ruleName(AlertRule rule, Long ruleId) {
        return rule != null && rule.getName() != null ? rule.getName() : String.valueOf(ruleId);
    }

    private MessageTemplate resolveTemplate(Long templateId) {
        if (templateId != null) {
            MessageTemplate template = messageTemplateMapper.selectById(templateId);
            if (template != null && template.getDeleted() == 0) {
                return template;
            }
        }
        MessageTemplate named = messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getBuiltin, true)
                .isNull(MessageTemplate::getChannelType)
                .eq(MessageTemplate::getName, TEMPLATE_NAME)
                .eq(MessageTemplate::getDeleted, 0)
                .last("LIMIT 1"));
        if (named != null) {
            return named;
        }
        // fallback: the newest built-in channel-agnostic HTML template (the digest one is newer than the daily one)
        return messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getBuiltin, true)
                .isNull(MessageTemplate::getChannelType)
                .eq(MessageTemplate::getFormat, TemplateFormat.HTML)
                .eq(MessageTemplate::getDeleted, 0)
                .orderByDesc(MessageTemplate::getId)
                .last("LIMIT 1"));
    }

    private String resolveChannelNames(List<AlertRecipient> recipients) {
        Map<Long, String> namesById = new LinkedHashMap<>();
        for (AlertRecipient recipient : recipients) {
            if (recipient.getMessageChannelId() == null) {
                continue;
            }
            namesById.computeIfAbsent(recipient.getMessageChannelId(), id -> {
                MessageChannel channel = messageChannelMapper.selectById(id);
                return channel != null ? channel.getName() : String.valueOf(id);
            });
        }
        return namesById.isEmpty() ? null : String.join(", ", namesById.values());
    }

    private Long parseTemplateId(ScheduledJob job) {
        if (job.getConfigJson() == null || job.getConfigJson().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(job.getConfigJson());
            JsonNode templateId = root.get("templateId");
            return templateId != null && templateId.isNumber() ? templateId.asLong() : null;
        } catch (JsonProcessingException e) {
            log.warn("Warning digest job id={} has invalid configJson: {}", job.getId(), e.getMessage());
            return null;
        }
    }

    /** Outcome of a digest run; {@code reason} is non-null only when nothing was sent. */
    public record DigestRunResult(boolean sent, String reason, int alertCount, int recipientCount, String messageNo) {
        public static DigestRunResult notSent(String reason) {
            return new DigestRunResult(false, reason, 0, 0, null);
        }
    }
}
