package com.alertworker.gateway.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.dto.GroupAssigneeView;
import com.alertworker.common.dto.RuleGroupView;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.SendType;
import com.alertworker.common.mapper.GroupAssigneeMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.mapper.RuleGroupMapper;
import com.alertworker.gateway.dto.ParsedAlert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageBuilder {

    private final RuleGroupMapper ruleGroupMapper;
    private final GroupAssigneeMapper groupAssigneeMapper;
    private final MessageChannelMapper messageChannelMapper;

    public AlertMessagePayload build(AlertRule rule, ParsedAlert parsedAlert) {
        List<AlertRecipient> recipients = resolveRecipients(rule, parsedAlert.getServiceType());

        return AlertMessagePayload.builder()
                .alertRuleId(rule.getId())
                .context(parsedAlert.getContext())
                .recipients(recipients)
                .build();
    }

    private List<AlertRecipient> resolveRecipients(AlertRule rule, String serviceType) {
        List<AlertRecipient> recipients = new ArrayList<>();

        List<RuleGroupView> ruleGroups = ruleGroupMapper.selectWithGroupSendType(rule.getId());
        if (CollectionUtils.isEmpty(ruleGroups)) {
            return recipients;
        }

        List<Long> groupIds = ruleGroups.stream().map(RuleGroupView::getGroupId).toList();
        List<GroupAssigneeView> groupAssignees = groupAssigneeMapper.selectWithAssignee(groupIds);
        if (CollectionUtils.isEmpty(groupAssignees)) {
            return recipients;
        }

        Map<ChannelType, Long> channelIdByType = resolveActiveChannelIds();

        for (RuleGroupView ruleGroup : ruleGroups) {
            SendType sendType = parseSendType(ruleGroup.getSendType());
            for (GroupAssigneeView ga : groupAssignees) {
                if (!ruleGroup.getGroupId().equals(ga.getGroupId())) {
                    continue;
                }
                if (!shouldReceive(ga.getServiceTypes(), sendType, serviceType)) {
                    continue;
                }
                ChannelType channelType = parseChannelType(ga.getChannelType());
                if (channelType == null) {
                    continue;
                }
                Long messageChannelId = channelIdByType.get(channelType);
                if (messageChannelId == null) {
                    log.warn("No active message channel found for type: {}", channelType);
                    continue;
                }

                recipients.add(AlertRecipient.builder()
                        .assigneeId(ga.getAssigneeId())
                        .cnName(ga.getCnName())
                        .phone(ga.getPhone())
                        .dingdingUsername(ga.getDingdingUsername())
                        .channelType(channelType)
                        .messageChannelId(messageChannelId)
                        .build());
            }
        }

        return recipients;
    }

    private Map<ChannelType, Long> resolveActiveChannelIds() {
        List<MessageChannel> channels = messageChannelMapper.selectList(
                new LambdaQueryWrapper<MessageChannel>().eq(MessageChannel::getDeleted, 0)
        );
        Map<ChannelType, Long> first = new HashMap<>();
        for (MessageChannel channel : channels) {
            first.putIfAbsent(channel.getChannelType(), channel.getId());
        }
        return first;
    }

    private boolean shouldReceive(String serviceTypes, SendType sendType, String serviceType) {
        if (sendType == SendType.ALL) {
            return true;
        }
        if (!StringUtils.hasText(serviceType)) {
            return true;
        }
        if (!StringUtils.hasText(serviceTypes)) {
            return true;
        }
        List<String> allowed = Arrays.asList(serviceTypes.split(","));
        return allowed.contains(serviceType);
    }

    private SendType parseSendType(String value) {
        if ("service_type".equalsIgnoreCase(value)) {
            return SendType.SERVICE_TYPE;
        }
        return SendType.ALL;
    }

    private ChannelType parseChannelType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ChannelType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown channel type in group_assignee: {}", value);
            return null;
        }
    }
}
