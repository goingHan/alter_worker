package com.alertworker.gateway.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.enums.AlertSeverity;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.gateway.dto.ParsedAlert;
import com.alertworker.gateway.parser.JsonAlertParser;
import com.alertworker.gateway.parser.PrometheusAlertParser;
import com.alertworker.common.messaging.AlertMessageProducer;
import com.alertworker.common.service.AlertMessageRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260916: shared ingest pipeline for the HTTP alert endpoints, dead-letter retry
// and the test-alert endpoint (forcedRule skips the matcher, overrideChannel forces a channel)
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertIngestService {

    private final PrometheusAlertParser prometheusAlertParser;
    private final JsonAlertParser jsonAlertParser;
    private final AlertRuleMatcher alertRuleMatcher;
    private final MessageBuilder messageBuilder;
    private final AlertMessageProducer alertMessageProducer;
    private final AlertMessageRecordService alertMessageRecordService;

    public record IngestResult(boolean success, int status, String messageNo, Long recordId,
                               AlertRule rule, int recipientCount, String error,
                               boolean silenced, boolean unmatched, boolean reportPending) {
    }

    public IngestResult ingest(Long apiKeyId, AlertType alertType, String rawPayload, String messageNo,
                               AlertRule forcedRule, MessageChannel overrideChannel) {
        try {
            ParsedAlert parsedAlert = alertType == AlertType.PROMETHEUS
                    ? prometheusAlertParser.parse(rawPayload)
                    : jsonAlertParser.parse(rawPayload);
            parsedAlert.getContext().put("messageNo", messageNo);

            AlertRule rule = forcedRule != null ? forcedRule : alertRuleMatcher.match(parsedAlert, alertType);
            if (rule == null) {
                // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: no rule matched — drop the alert instead of failing it:
                // nothing is enqueued or retried, the run is recorded as UNMATCHED, and the caller gets 2xx (the alert
                // was accepted and handled) so upstream alerting does not re-send it forever.
                String reason = "No matching alert rule";
                log.warn("No matching alert rule found for {} alert, messageNo={} — dropped", alertType, messageNo);
                AlertMessage msg = alertMessageRecordService.create(
                        apiKeyId, null, parsedAlert.getContext(), messageNo, rawPayload, null);
                alertMessageRecordService.transitionToUnmatched(msg.getId(), reason);
                return new IngestResult(true, 200, messageNo, msg.getId(), null, 0, reason, false, true, false);
            }

            // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: the rule owns the alert level, so overwrite the severity
            // carried by the incoming alert everywhere a template may read it before anything is persisted/enqueued.
            applyRuleSeverity(parsedAlert.getContext(), rule.getSeverity());

            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: a silenced rule drops the alert here — no recipient or
            // channel resolution, nothing enqueued; the run is recorded as SILENCED so the dashboard can show what
            // the silence blocked. The request still answers 2xx because the alert was accepted and handled.
            if (Boolean.TRUE.equals(rule.getSilenced())) {
                log.info("Alert rule {} is silenced, dropping {} alert, messageNo={}", rule.getId(), alertType, messageNo);
                AlertMessage msg = alertMessageRecordService.create(
                        apiKeyId, rule.getId(), parsedAlert.getContext(), messageNo, rawPayload, null);
                alertMessageRecordService.transitionToSilenced(msg.getId(), rule.getId());
                return new IngestResult(true, 200, messageNo, msg.getId(), rule, 0, null, true, false, false);
            }

            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: a report-only rule stores the alert without enqueueing —
            // no recipient/channel resolution, nothing published to RabbitMQ; the run is recorded as REPORT_PENDING so
            // the daily-report job can aggregate it later. Silenced rules are checked first and take precedence.
            if (Boolean.TRUE.equals(rule.getReportOnly())) {
                log.info("Alert rule {} is report-only, storing {} alert as REPORT_PENDING, messageNo={}", rule.getId(), alertType, messageNo);
                AlertMessage msg = alertMessageRecordService.create(apiKeyId, rule.getId(), parsedAlert.getContext(), messageNo, rawPayload, null);
                msg.setMessageType("ALERT");
                alertMessageRecordService.transitionToReportPending(msg.getId(), rule.getId());
                return new IngestResult(true, 200, messageNo, msg.getId(), rule, 0, null, false, false, true);
            }

            AlertMessagePayload mqPayload = messageBuilder.build(rule, parsedAlert, overrideChannel);
            String channelNames = messageBuilder.resolveChannelNames(mqPayload.getRecipients());
            AlertMessage message = alertMessageRecordService.create(
                    apiKeyId, rule.getId(), parsedAlert.getContext(), messageNo, rawPayload, channelNames);
            mqPayload.setAlertMessageId(message.getId());
            mqPayload.setMessageNo(messageNo);

            alertMessageProducer.send(mqPayload);
            alertMessageRecordService.transitionToGwSent(message.getId());

            return new IngestResult(true, 200, messageNo, message.getId(), rule, mqPayload.getRecipients().size(), null, false, false, false);
        } catch (Exception e) {
            log.error("Failed to handle {} alert, messageNo={}", alertType, messageNo, e);
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            AlertMessage msg = alertMessageRecordService.create(
                    apiKeyId, null, Map.of("error", reason), messageNo, rawPayload, null);
            alertMessageRecordService.transitionToFailed(msg.getId(), MessageSubStatus.gw_failed, reason);
            return new IngestResult(false, 500, messageNo, msg.getId(), null, 0, reason, false, false, false);
        }
    }

    /**
     * Overwrites the severity carried by the incoming alert with the rule's own level everywhere a template may read
     * it: {@code severity} (JSON top level), {@code labels.severity} / {@code commonLabels.severity} (Prometheus) and
     * each entry of {@code alerts[].labels.severity}. {@code ruleSeverity} is added as an explicit alias.
     */
    private void applyRuleSeverity(Map<String, Object> context, AlertSeverity severity) {
        if (context == null || severity == null) {
            return;
        }
        String value = severity.name();
        context.put("severity", value);
        context.put("ruleSeverity", value);
        overwriteNestedSeverity(context.get("labels"), value);
        overwriteNestedSeverity(context.get("commonLabels"), value);
        Object alerts = context.get("alerts");
        if (alerts instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> alertMap) {
                    overwriteNestedSeverity(alertMap.get("labels"), value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void overwriteNestedSeverity(Object labels, String value) {
        if (labels instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put("severity", value);
        }
    }
}
