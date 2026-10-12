package com.alertworker.gateway.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.service.RuleRecipientResolver;
import com.alertworker.gateway.dto.ParsedAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageBuilder {

    private final MessageChannelMapper messageChannelMapper;
    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: rule->recipient resolution moved to common so the processor's
    // warning-digest executor can reuse the exact same semantics
    private final RuleRecipientResolver ruleRecipientResolver;

    public AlertMessagePayload build(AlertRule rule, ParsedAlert parsedAlert) {
        return build(rule, parsedAlert, null);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: overrideChannel (test alert) forces every resolved recipient
    // onto the given channel record, regardless of the recipient's own channel-type preference
    public AlertMessagePayload build(AlertRule rule, ParsedAlert parsedAlert, MessageChannel overrideChannel) {
        List<AlertRecipient> recipients = ruleRecipientResolver.resolve(rule, parsedAlert.getServiceType(), overrideChannel);

        return AlertMessagePayload.builder()
                .alertRuleId(rule.getId())
                .context(parsedAlert.getContext())
                .recipients(recipients)
                .build();
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: distinct channel names of the resolved recipients for history display
    public String resolveChannelNames(List<AlertRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return null;
        }
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
}
