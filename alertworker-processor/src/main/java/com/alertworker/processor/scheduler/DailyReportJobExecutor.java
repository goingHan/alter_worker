package com.alertworker.processor.scheduler;

import com.alertworker.common.analytics.AlertMessageAnalytics;
import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.Assignee;
import com.alertworker.common.entity.DailyReport;
import com.alertworker.common.entity.DailyReportChannel;
import com.alertworker.common.entity.DailyReportRecipient;
import com.alertworker.common.entity.DailyReportRule;
import com.alertworker.common.entity.DailyReportTrendRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.MessageKind;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.TemplateFormat;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AssigneeMapper;
import com.alertworker.common.mapper.DailyReportChannelMapper;
import com.alertworker.common.mapper.DailyReportMapper;
import com.alertworker.common.mapper.DailyReportRecipientMapper;
import com.alertworker.common.mapper.DailyReportRuleMapper;
import com.alertworker.common.mapper.DailyReportTrendRuleMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.messaging.AlertMessageProducer;
import com.alertworker.common.scheduler.JobExecutor;
import com.alertworker.common.service.AlertMessageRecordService;
import com.alertworker.common.service.JinjavaTemplateService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily-report aggregation job ({@code jobType = DAILY_REPORT}).
 *
 * <p>Aggregates the run day's alert rows (Asia/Shanghai {@code [00:00, 24:00)}) for the report's rules — both
 * {@code REPORT_PENDING} rows held by report-only rules and normally delivered alerts — computes by-rule /
 * by-severity / by-service_type counts plus a bounded detail list, resolves and renders the report template once,
 * creates one {@code alert_message} row ({@code message_type = REPORT}), and enqueues a REPORT payload (rendered
 * content + optional hosted-HTML URL) through the common producer.
 *
 * <p><b>Full-day snapshot semantics (2026-10-10):</b> every run re-aggregates the whole day regardless of whether a
 * previous run already reported a row, so running at 10:00 and again at 12:00 produces two cumulative reports that
 * both contain the 00:00-10:00 alerts. When the window has no alerts an (empty) report is still generated. The
 * {@code report_message_id} back-reference is written to the aggregated rows for traceability only and is no longer
 * used to filter them.
 *
 * <p>When the resolved template {@code format = HTML}, the rendered self-contained HTML document is uploaded to the
 * configured object storage and the report message records {@code content_url}/{@code object_key}; the Worker then
 * embeds that HTML in email and sends the link in DingDing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyReportJobExecutor implements JobExecutor {

    public static final String JOB_TYPE = "DAILY_REPORT";
    private static final String MESSAGE_NO_PREFIX = "REPORT-";
    private static final String MESSAGE_TYPE_ALERT = "ALERT";
    private static final String MESSAGE_TYPE_REPORT = "REPORT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DETAIL_ROWS = 200;

    private final ObjectMapper objectMapper;
    private final AlertRowExtractor alertRowExtractor;
    private final DailyReportMapper dailyReportMapper;
    private final DailyReportRuleMapper dailyReportRuleMapper;
    private final DailyReportRecipientMapper dailyReportRecipientMapper;
    private final DailyReportChannelMapper dailyReportChannelMapper;
    private final DailyReportTrendRuleMapper dailyReportTrendRuleMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertMessageMapper alertMessageMapper;
    private final AlertMessageAnalytics alertMessageAnalytics;
    private final AssigneeMapper assigneeMapper;
    private final MessageChannelMapper messageChannelMapper;
    private final MessageTemplateMapper messageTemplateMapper;
    private final AlertMessageRecordService alertMessageRecordService;
    private final AlertMessageProducer alertMessageProducer;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void execute(ScheduledJob job) {
        Long reportId = parseReportId(job);
        if (reportId == null) {
            log.warn("Scheduled DAILY_REPORT job id={} has no valid reportId in configJson, skipping", job.getId());
            return;
        }
        DailyReport report = dailyReportMapper.selectById(reportId);
        if (report == null) {
            log.warn("Scheduled DAILY_REPORT job id={} references missing daily_report id={}, skipping",
                    job.getId(), reportId);
            return;
        }
        runReport(report);
    }

    /**
     * #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: manual/periodic entry point. Returns a result describing what
     * happened so "立即生成" can report WHY nothing was sent instead of silently answering success.
     */
    public ReportRunResult runReport(DailyReport report) {
        if (!Boolean.TRUE.equals(report.getEnabled())) {
            log.info("Daily report id={} ({}) is disabled, skipping", report.getId(), report.getName());
            return ReportRunResult.notSent("disabled");
        }

        // 2. The report's rules: ids for the aggregation query + a lookup map for alertType/name.
        List<Long> ruleIds = dailyReportRuleMapper.selectList(
                        new LambdaQueryWrapper<DailyReportRule>()
                                .eq(DailyReportRule::getReportId, report.getId()))
                .stream()
                .map(DailyReportRule::getRuleId)
                .distinct()
                .toList();
        if (ruleIds.isEmpty()) {
            log.info("Daily report id={} ({}) has no rules configured, nothing to aggregate",
                    report.getId(), report.getName());
            return ReportRunResult.notSent("no-rules");
        }
        Map<Long, AlertRule> rulesById = new HashMap<>();
        for (Long ruleId : ruleIds) {
            AlertRule rule = alertRuleMapper.selectById(ruleId);
            if (rule != null) {
                rulesById.put(ruleId, rule);
            }
        }

        // 3. Aggregation window: the run's calendar day in Asia/Shanghai, narrowed to the report's configured
        // statistic window when present (stat_start_time inclusive, stat_end_time inclusive at minute granularity).
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime start = windowStart(report, today);
        LocalDateTime end = windowEnd(report, today);

        // 4. Alert rows for the report's rules over the run day — both REPORT_PENDING (report-only rules) and normally
        // delivered alerts, so a report may summarize any rule. This is a FULL-DAY SNAPSHOT: every run re-aggregates
        // the whole day regardless of whether a previous run already reported the row, so running at 10:00 and again at
        // 12:00 yields two cumulative reports that both contain the 00:00-10:00 alerts. SILENCED rows are excluded
        // (they were dropped); TEST/REPORT rows carry no alert_rule_id and are therefore not matched.
        List<AlertMessage> rows = alertMessageMapper.selectList(new LambdaQueryWrapper<AlertMessage>()
                .in(AlertMessage::getAlertRuleId, ruleIds)
                .eq(AlertMessage::getMessageType, MESSAGE_TYPE_ALERT)
                .ne(AlertMessage::getStatus, MessageStatus.SILENCED)
                .ge(AlertMessage::getCreatedAt, start)
                .lt(AlertMessage::getCreatedAt, end));
        if (rows.isEmpty()) {
            // No alerts in the window — still generate an (empty) report so the run is visible and the link chain is
            // exercised; the recipient/template checks below still apply.
            log.info("Daily report id={} ({}) has no alert data for {}, generating an empty snapshot",
                    report.getId(), report.getName(), today);
        }

        // 5+6. Trend monitoring items (alert rules whose per-hour counts are summarized) and the report context.
        List<Long> trendRuleIds = dailyReportTrendRuleMapper.selectList(
                        new LambdaQueryWrapper<DailyReportTrendRule>()
                                .eq(DailyReportTrendRule::getReportId, report.getId()))
                .stream()
                .map(DailyReportTrendRule::getRuleId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Object> reportContext = buildReportContext(today, start, end, rows, rulesById, trendRuleIds);

        // 7. Recipients: daily_report_recipient (assignee + channel type) x daily_report_channel bindings
        // (per-type default-channel fallback).
        List<AlertRecipient> recipients = resolveRecipients(report);
        if (recipients.isEmpty()) {
            log.warn("Daily report id={} ({}) resolved no recipients, nothing to send",
                    report.getId(), report.getName());
            return ReportRunResult.notSent("no-recipients");
        }
        String channelNames = resolveChannelNames(recipients);

        // 8. Resolve and render the report template once (the data-processing module owns report rendering now). A
        //    hosted HTML template is uploaded to object storage and its public URL is carried in the payload.
        MessageTemplate template = resolveReportTemplate(report);
        if (template == null) {
            log.error("Daily report id={} ({}) resolved no template, nothing to send",
                    report.getId(), report.getName());
            return ReportRunResult.notSent("no-template");
        }
        String messageNo = MESSAGE_NO_PREFIX + MessageNoGenerator.generate();
        String renderedContent = jinjavaTemplateService.render(template.getContent(), reportContext);

        String objectKey = null;
        String contentUrl = null;
        if (template.getFormat() == TemplateFormat.HTML) {
            String relative = "reports/" + today.getYear()
                    + "/" + String.format("%02d", today.getMonthValue())
                    + "/" + String.format("%02d", today.getDayOfMonth())
                    + "/" + messageNo + ".html";
            objectKey = storageProperties.objectKey(relative);
            objectStorageService.put(objectKey, renderedContent.getBytes(StandardCharsets.UTF_8),
                    "text/html; charset=utf-8");
            contentUrl = objectStorageService.publicUrl(objectKey);
            log.info("Daily report id={} uploaded HTML report to objectKey={} contentUrl={}",
                    report.getId(), objectKey, contentUrl);
        }

        // 9. Create the REPORT record, enqueue, and only then mark the aggregated rows as claimed.
        AlertMessage reportMessage = alertMessageRecordService.create(
                null, null, reportContext, messageNo, null, channelNames);
        // create() leaves message_type to the DB default 'ALERT'; persist 'REPORT' and the hosted-file location.
        LambdaUpdateWrapper<AlertMessage> reportUpdate = new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, reportMessage.getId())
                .set(AlertMessage::getMessageType, MESSAGE_TYPE_REPORT)
                .set(AlertMessage::getContentUrl, contentUrl)
                .set(AlertMessage::getObjectKey, objectKey);
        alertMessageMapper.update(null, reportUpdate);
        alertMessageRecordService.transitionToGwSent(reportMessage.getId());

        AlertMessagePayload payload = AlertMessagePayload.builder()
                .kind(MessageKind.REPORT)
                .alertMessageId(reportMessage.getId())
                .messageNo(messageNo)
                .alertRuleId(null)
                .reportTemplateId(report.getTemplateId())
                .renderedContent(renderedContent)
                .htmlUrl(contentUrl)
                .context(reportContext)
                .recipients(recipients)
                .build();
        alertMessageProducer.send(payload);

        // Informational back-reference: stamp the aggregated rows with this report's id so the last report that included
        // each row is traceable. This is NOT used to filter future runs (full-day snapshot), so a later run simply
        // overwrites it. Skipped when the day was empty (rows is empty -> the update is a no-op anyway).
        if (!rows.isEmpty()) {
            alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                    .in(AlertMessage::getId, rows.stream().map(AlertMessage::getId).toList())
                    .set(AlertMessage::getReportMessageId, reportMessage.getId()));
        }
        log.info("Daily report id={} ({}) aggregated {} alert(s) for {} into report message no={} for {} recipient(s)",
                report.getId(), report.getName(), rows.size(), today, messageNo, recipients.size());
        return new ReportRunResult(true, null, rows.size(), recipients.size(), messageNo);
    }

    /**
     * Template resolution for a report: the report's explicit template, then the built-in report template (an HTML
     * one preferred), then the global default template. Built-in report templates are channel-agnostic
     * ({@code channel_type IS NULL}).
     */
    private MessageTemplate resolveReportTemplate(DailyReport report) {
        if (report.getTemplateId() != null) {
            MessageTemplate template = messageTemplateMapper.selectById(report.getTemplateId());
            if (template != null && template.getDeleted() == 0) {
                return template;
            }
        }
        MessageTemplate htmlBuiltin = messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getBuiltin, true)
                .isNull(MessageTemplate::getChannelType)
                .eq(MessageTemplate::getFormat, TemplateFormat.HTML)
                .eq(MessageTemplate::getDeleted, 0)
                .orderByAsc(MessageTemplate::getId)
                .last("LIMIT 1"));
        if (htmlBuiltin != null) {
            return htmlBuiltin;
        }
        MessageTemplate anyBuiltin = messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getBuiltin, true)
                .isNull(MessageTemplate::getChannelType)
                .eq(MessageTemplate::getDeleted, 0)
                .orderByAsc(MessageTemplate::getId)
                .last("LIMIT 1"));
        if (anyBuiltin != null) {
            return anyBuiltin;
        }
        return messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getIsDefault, true)
                .eq(MessageTemplate::getDeleted, 0)
                .last("LIMIT 1"));
    }

    /**
     * Aggregation-window start within the run day: the report's configured {@code statStartTime}, or 00:00 when
     * unset.
     */
    private LocalDateTime windowStart(DailyReport report, LocalDate today) {
        return report.getStatStartTime() != null ? today.atTime(report.getStatStartTime()) : today.atStartOfDay();
    }

    /**
     * Aggregation-window end (exclusive) within the run day: {@code statEndTime} is inclusive at minute granularity,
     * so 23:59 becomes the next day 00:00. Falls back to the full day when unset.
     */
    private LocalDateTime windowEnd(DailyReport report, LocalDate today) {
        return report.getStatEndTime() != null
                ? today.atTime(report.getStatEndTime()).plusMinutes(1)
                : today.plusDays(1).atStartOfDay();
    }

    private Long parseReportId(ScheduledJob job) {
        if (job.getConfigJson() == null || job.getConfigJson().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(job.getConfigJson());
            JsonNode reportId = root.get("reportId");
            return reportId != null && reportId.isNumber() ? reportId.asLong() : null;
        } catch (JsonProcessingException e) {
            log.warn("Scheduled DAILY_REPORT job id={} has invalid configJson: {}", job.getId(), e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildReportContext(LocalDate today, LocalDateTime start, LocalDateTime end,
                                                    List<AlertMessage> rows,
                                                    Map<Long, AlertRule> rulesById,
                                                    List<Long> trendRuleIds) {
        Map<Long, Integer> ruleCounts = new LinkedHashMap<>();
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        Map<String, Integer> serviceTypeCounts = new LinkedHashMap<>();
        List<Map<String, Object>> alertDetails = new ArrayList<>();

        int detailCap = Math.min(rows.size(), MAX_DETAIL_ROWS);
        for (int i = 0; i < rows.size(); i++) {
            AlertMessage row = rows.get(i);
            Long ruleId = row.getAlertRuleId() != null ? row.getAlertRuleId() : 0L;
            AlertRowExtractor.RowInfo info = alertRowExtractor.extract(row, rulesById.get(ruleId));
            ruleCounts.merge(ruleId, 1, Integer::sum);
            severityCounts.merge(info.severity(), 1, Integer::sum);
            serviceTypeCounts.merge(info.serviceType(), 1, Integer::sum);
            if (i < detailCap) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("messageNo", row.getMessageNo());
                detail.put("ruleName", ruleName(rulesById.get(ruleId), ruleId));
                detail.put("severity", info.severity());
                detail.put("serviceType", info.serviceType());
                detail.put("createdAt", row.getCreatedAt() == null
                        ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(row.getCreatedAt()));
                detail.put("summary", info.summary());
                alertDetails.add(detail);
            }
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", today.toString());
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
        context.put("alerts", alertDetails);
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: per-rule trend monitoring items — each selected rule gets
        // 平均/中位数/P20/P40/P60/P80/最大/最小 of its per-hour alert counts over the run day.
        if (trendRuleIds != null && !trendRuleIds.isEmpty()) {
            context.put("trends", buildTrends(start, end, trendRuleIds, rulesById));
        }
        return context;
    }

    /**
     * Per-rule trend for the run window: for each selected monitoring rule, takes its alert counts bucketed by
     * hour (0–23, missing hours = 0) as the sample set and computes 平均 / 中位数 / P20 / P40 / P60 / P80 / 最大 / 最小.
     * Percentiles use linear interpolation so they match the dashboard's {@code PERCENTILE_CONT} semantics.
     */
    private List<Map<String, Object>> buildTrends(LocalDateTime start, LocalDateTime end, List<Long> trendRuleIds,
                                                  Map<Long, AlertRule> rulesById) {
        Map<Long, int[]> hourlyByRule = new LinkedHashMap<>();
        for (Long ruleId : trendRuleIds) {
            hourlyByRule.put(ruleId, new int[24]);
        }
        for (Map<String, Object> cell : alertMessageAnalytics.countHourlyByRule(trendRuleIds, start, end)) {
            Long ruleId = toLong(cell.get("ruleId"));
            if (ruleId == null || !(cell.get("hourOfDay") instanceof Number hour) || !(cell.get("cnt") instanceof Number cnt)) {
                continue;
            }
            int[] series = hourlyByRule.get(ruleId);
            int h = hour.intValue();
            if (series != null && h >= 0 && h < series.length) {
                series[h] = cnt.intValue();
            }
        }

        List<Map<String, Object>> trends = new ArrayList<>();
        for (Long ruleId : trendRuleIds) {
            int[] series = hourlyByRule.get(ruleId);
            if (series == null) {
                continue;
            }
            // Only sample the hourly buckets that fall inside the configured window (full day => all 24 hours, so a
            // report without a stat window keeps its previous behaviour).
            List<Integer> values = new ArrayList<>(series.length);
            LocalDate day = start.toLocalDate();
            for (int h = 0; h < series.length; h++) {
                LocalDateTime hourStart = day.atTime(h, 0);
                LocalDateTime hourEnd = hourStart.plusHours(1);
                if (hourEnd.isAfter(start) && hourStart.isBefore(end)) {
                    values.add(series[h]);
                }
            }
            List<Integer> sorted = new ArrayList<>(values);
            sorted.sort(Integer::compareTo);
            double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0);

            AlertRule rule = rulesById.get(ruleId);
            if (rule == null) {
                rule = alertRuleMapper.selectById(ruleId);
            }

            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("ruleId", ruleId);
            trend.put("ruleName", ruleName(rule, ruleId));
            trend.put("total", values.stream().mapToInt(Integer::intValue).sum());
            trend.put("avg", round2(avg));
            trend.put("median", round2(percentile(sorted, 50)));
            trend.put("p20", round2(percentile(sorted, 20)));
            trend.put("p40", round2(percentile(sorted, 40)));
            trend.put("p60", round2(percentile(sorted, 60)));
            trend.put("p80", round2(percentile(sorted, 80)));
            trend.put("max", sorted.get(sorted.size() - 1));
            trend.put("min", sorted.get(0));
            trend.put("series", values);
            trends.add(trend);
        }
        return trends;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    /** Linear-interpolation percentile (type 7, numpy default) on an ascending-sorted list. */
    private double percentile(List<Integer> sorted, double percentile) {
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        if (n == 1) {
            return sorted.get(0);
        }
        double rank = percentile / 100.0 * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double frac = rank - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String ruleName(AlertRule rule, Long ruleId) {
        return rule != null && rule.getName() != null ? rule.getName() : String.valueOf(ruleId);
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

    /**
     * Mirrors {@code MessageBuilder.resolveRuleChannelIds} fallback: per-type default channel ({@code is_default}
     * first, else the earliest active channel by id). Used per channel type without an explicit
     * {@code daily_report_channel} binding.
     */
    private List<AlertRecipient> resolveRecipients(DailyReport report) {
        List<DailyReportRecipient> recipientRows = dailyReportRecipientMapper.selectList(
                new LambdaQueryWrapper<DailyReportRecipient>()
                        .eq(DailyReportRecipient::getReportId, report.getId()));
        if (recipientRows.isEmpty()) {
            log.info("Daily report id={} ({}) has no recipients configured; DingDing channels will be sent to "
                    + "without @ anyone", report.getId(), report.getName());
        }

        Map<ChannelType, List<Long>> explicitChannelIdsByType = new LinkedHashMap<>();
        for (DailyReportChannel rc : dailyReportChannelMapper.selectList(
                new LambdaQueryWrapper<DailyReportChannel>()
                        .eq(DailyReportChannel::getReportId, report.getId()))) {
            MessageChannel channel = messageChannelMapper.selectById(rc.getChannelId());
            if (channel == null) {
                log.warn("Daily report id={} references missing message_channel id={}, skipping",
                        report.getId(), rc.getChannelId());
                continue;
            }
            explicitChannelIdsByType.computeIfAbsent(channel.getChannelType(), k -> new ArrayList<>())
                    .add(channel.getId());
        }
        Map<ChannelType, MessageChannel> defaultChannelByType = loadDefaultChannels();

        Map<String, AlertRecipient> recipientsByKey = new LinkedHashMap<>();
        for (DailyReportRecipient rec : recipientRows) {
            Assignee assignee = assigneeMapper.selectById(rec.getAssigneeId());
            if (assignee == null) {
                log.warn("Daily report id={} references missing assignee id={}, skipping",
                        report.getId(), rec.getAssigneeId());
                continue;
            }
            // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: disabled assignees receive no report (no email, no @)
            if (Boolean.FALSE.equals(assignee.getEnabled())) {
                log.info("Daily report id={} skips disabled assignee id={} ({})",
                        report.getId(), assignee.getId(), assignee.getCnName());
                continue;
            }
            ChannelType channelType = rec.getChannelType();
            if (channelType == null) {
                log.warn("Daily report id={} recipient assignee id={} has no channel_type, skipping",
                        report.getId(), rec.getAssigneeId());
                continue;
            }
            List<Long> channelIds = explicitChannelIdsByType.get(channelType);
            if (channelIds == null || channelIds.isEmpty()) {
                MessageChannel fallback = defaultChannelByType.get(channelType);
                if (fallback == null) {
                    log.warn("Daily report id={} has no channel configured for type {} (assignee id={}), skipping",
                            report.getId(), channelType, rec.getAssigneeId());
                    continue;
                }
                channelIds = List.of(fallback.getId());
            }
            for (Long channelId : channelIds) {
                recipientsByKey.put(rec.getAssigneeId() + ":" + channelType + ":" + channelId,
                        AlertRecipient.builder()
                                .assigneeId(assignee.getId())
                                .cnName(assignee.getCnName())
                                .phone(assignee.getPhone())
                                .dingdingUsername(assignee.getDingdingUsername())
                                .email(assignee.getEmail())
                                .channelType(channelType)
                                .messageChannelId(channelId)
                                .build());
            }
        }

        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: a DingDing group robot delivers to the group regardless of
        // who is @-ed, so a report bound to a DingDing channel must reach it even when no DingDing recipient is
        // configured (recipients may be empty on purpose). Mirror the recipient path's channel fallback and add a
        // placeholder recipient (no phone -> no @). Email still requires a configured recipient (address).
        if (!hasChannelType(recipientsByKey, ChannelType.DINGDING)) {
            List<Long> dingdingChannelIds = explicitChannelIdsByType.get(ChannelType.DINGDING);
            if (dingdingChannelIds == null || dingdingChannelIds.isEmpty()) {
                MessageChannel fallback = defaultChannelByType.get(ChannelType.DINGDING);
                dingdingChannelIds = fallback != null ? List.of(fallback.getId()) : List.of();
            }
            if (!dingdingChannelIds.isEmpty()) {
                log.info("Daily report id={} has no DingDing recipient; sending to DingDing channel(s) {} without @",
                        report.getId(), dingdingChannelIds);
            }
            for (Long channelId : dingdingChannelIds) {
                recipientsByKey.put("report:DINGDING:" + channelId, AlertRecipient.builder()
                        .cnName("日报推送")
                        .channelType(ChannelType.DINGDING)
                        .messageChannelId(channelId)
                        .build());
            }
        }

        log.info("Daily report id={} resolved {} recipient(s): {}", report.getId(), recipientsByKey.size(),
                recipientsByKey.values().stream()
                        .map(r -> r.getChannelType() + "#" + r.getMessageChannelId())
                        .distinct()
                        .toList());
        return new ArrayList<>(recipientsByKey.values());
    }

    private boolean hasChannelType(Map<String, AlertRecipient> recipients, ChannelType type) {
        return recipients.values().stream().anyMatch(r -> r.getChannelType() == type);
    }

    private Map<ChannelType, MessageChannel> loadDefaultChannels() {
        List<MessageChannel> channels = messageChannelMapper.selectList(
                new LambdaQueryWrapper<MessageChannel>()
                        .eq(MessageChannel::getDeleted, 0)
                        .orderByAsc(MessageChannel::getId));
        Map<ChannelType, MessageChannel> firstByType = new LinkedHashMap<>();
        Map<ChannelType, MessageChannel> defaultByType = new LinkedHashMap<>();
        for (MessageChannel channel : channels) {
            firstByType.putIfAbsent(channel.getChannelType(), channel);
            if (Boolean.TRUE.equals(channel.getIsDefault())) {
                defaultByType.putIfAbsent(channel.getChannelType(), channel);
            }
        }
        Map<ChannelType, MessageChannel> chosenByType = new LinkedHashMap<>();
        for (MessageChannel channel : firstByType.values()) {
            chosenByType.put(channel.getChannelType(),
                    defaultByType.getOrDefault(channel.getChannelType(), channel));
        }
        return chosenByType;
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

    /**
     * Outcome of a report run. {@code reason} is non-null only when nothing was sent (e.g. {@code no-rules},
     * {@code no-recipients}, {@code no-template}, {@code disabled}). An empty day still sends a report, so there is
     * no "no-data" reason.
     */
    public record ReportRunResult(boolean sent, String reason, int alertCount, int recipientCount, String messageNo) {
        public static ReportRunResult notSent(String reason) {
            return new ReportRunResult(false, reason, 0, 0, null);
        }
    }
}
