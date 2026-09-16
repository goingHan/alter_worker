package com.alertworker.gateway.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.gateway.dto.ParsedAlert;
import com.alertworker.gateway.parser.JsonAlertParser;
import com.alertworker.gateway.parser.PrometheusAlertParser;
import com.alertworker.gateway.rabbitmq.AlertMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final GatewayAlertMessageService gatewayAlertMessageService;

    public record IngestResult(boolean success, int status, String messageNo, Long recordId,
                               AlertRule rule, int recipientCount, String error) {
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
                log.warn("No matching alert rule found for {} alert, messageNo={}", alertType, messageNo);
                AlertMessage msg = gatewayAlertMessageService.create(
                        apiKeyId, null, parsedAlert.getContext(), messageNo, rawPayload, null);
                gatewayAlertMessageService.transitionToFailed(msg.getId(), MessageSubStatus.gw_failed, "No matching alert rule");
                return new IngestResult(false, 400, messageNo, msg.getId(), null, 0, "No matching alert rule");
            }

            AlertMessagePayload mqPayload = messageBuilder.build(rule, parsedAlert, overrideChannel);
            String channelNames = messageBuilder.resolveChannelNames(mqPayload.getRecipients());
            AlertMessage message = gatewayAlertMessageService.create(
                    apiKeyId, rule.getId(), parsedAlert.getContext(), messageNo, rawPayload, channelNames);
            mqPayload.setAlertMessageId(message.getId());
            mqPayload.setMessageNo(messageNo);

            alertMessageProducer.send(mqPayload);
            gatewayAlertMessageService.transitionToGwSent(message.getId());

            return new IngestResult(true, 200, messageNo, message.getId(), rule, mqPayload.getRecipients().size(), null);
        } catch (Exception e) {
            log.error("Failed to handle {} alert, messageNo={}", alertType, messageNo, e);
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            AlertMessage msg = gatewayAlertMessageService.create(
                    apiKeyId, null, Map.of("error", reason), messageNo, rawPayload, null);
            gatewayAlertMessageService.transitionToFailed(msg.getId(), MessageSubStatus.gw_failed, reason);
            return new IngestResult(false, 500, messageNo, msg.getId(), null, 0, reason);
        }
    }
}
