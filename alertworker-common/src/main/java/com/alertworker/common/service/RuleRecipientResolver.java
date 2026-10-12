package com.alertworker.common.service;

import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.dto.GroupAssigneeView;
import com.alertworker.common.dto.RuleAssigneeView;
import com.alertworker.common.dto.RuleChannelView;
import com.alertworker.common.dto.RuleGroupView;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.SendType;
import com.alertworker.common.mapper.AlertRuleChannelMapper;
import com.alertworker.common.mapper.GroupAssigneeMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.mapper.RuleAssigneeMapper;
import com.alertworker.common.mapper.RuleGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the recipients of an alert rule (its groups, direct assignees and channel bindings) into a de-duplicated
 * {@link AlertRecipient} list.
 *
 * <p>This was extracted from the gateway {@code MessageBuilder} into the common module (2026-10-12) so the
 * data-processing module's warning-digest executor can reuse the exact same rule-recipient semantics when it merges
 * the matched rules' recipients into one digest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleRecipientResolver {

    private final RuleGroupMapper ruleGroupMapper;
    private final GroupAssigneeMapper groupAssigneeMapper;
    private final MessageChannelMapper messageChannelMapper;
    private final RuleAssigneeMapper ruleAssigneeMapper;
    private final AlertRuleChannelMapper alertRuleChannelMapper;

    /**
     * Resolves the rule's recipients for the given service type. {@code overrideChannel} (test alerts) forces every
     * resolved recipient onto that channel record regardless of the recipient's own channel-type preference.
     */
    public List<AlertRecipient> resolve(AlertRule rule, String serviceType, MessageChannel overrideChannel) {
        Map<String, AlertRecipient> recipientsByKey = new LinkedHashMap<>();
        Map<ChannelType, List<Long>> channelIdsByType = overrideChannel != null
                ? Map.of(overrideChannel.getChannelType(), List.of(overrideChannel.getId()))
                : resolveRuleChannelIds(rule.getId());

        List<RuleGroupView> ruleGroups = ruleGroupMapper.selectWithGroupSendType(rule.getId());
        if (!CollectionUtils.isEmpty(ruleGroups)) {
            List<Long> groupIds = ruleGroups.stream().map(RuleGroupView::getGroupId).toList();
            List<GroupAssigneeView> groupAssignees = groupAssigneeMapper.selectWithAssignee(groupIds);
            if (!CollectionUtils.isEmpty(groupAssignees)) {
                for (RuleGroupView ruleGroup : ruleGroups) {
                    SendType sendType = parseSendType(ruleGroup.getSendType());
                    for (GroupAssigneeView ga : groupAssignees) {
                        if (!ruleGroup.getGroupId().equals(ga.getGroupId())) {
                            continue;
                        }
                        if (!shouldReceive(ga.getServiceTypes(), sendType, serviceType)) {
                            continue;
                        }
                        if (overrideChannel != null) {
                            putRecipient(recipientsByKey, ga.getAssigneeId(), ga.getCnName(), ga.getPhone(),
                                    ga.getDingdingUsername(), ga.getEmail(),
                                    overrideChannel.getChannelType(), overrideChannel.getId());
                            continue;
                        }
                        ChannelType channelType = parseChannelType(ga.getChannelType());
                        if (channelType == null) {
                            continue;
                        }
                        for (Long messageChannelId : channelIdsByType.getOrDefault(channelType, List.of())) {
                            putRecipient(recipientsByKey, ga.getAssigneeId(), ga.getCnName(), ga.getPhone(),
                                    ga.getDingdingUsername(), ga.getEmail(), channelType, messageChannelId);
                        }
                    }
                }
            }
        }

        for (RuleAssigneeView ra : ruleAssigneeMapper.selectWithAssignee(rule.getId())) {
            if (overrideChannel != null) {
                putRecipient(recipientsByKey, ra.getAssigneeId(), ra.getCnName(), ra.getPhone(),
                        ra.getDingdingUsername(), ra.getEmail(),
                        overrideChannel.getChannelType(), overrideChannel.getId());
                continue;
            }
            ChannelType channelType = parseChannelType(ra.getChannelType());
            if (channelType == null) {
                continue;
            }
            for (Long messageChannelId : channelIdsByType.getOrDefault(channelType, List.of())) {
                putRecipient(recipientsByKey, ra.getAssigneeId(), ra.getCnName(), ra.getPhone(),
                        ra.getDingdingUsername(), ra.getEmail(), channelType, messageChannelId);
            }
        }

        // forced-channel test with no configured recipients: still send once so the channel itself gets verified
        if (overrideChannel != null && recipientsByKey.isEmpty()) {
            recipientsByKey.put("test:" + overrideChannel.getId(), AlertRecipient.builder()
                    .cnName("测试接收人")
                    .channelType(overrideChannel.getChannelType())
                    .messageChannelId(overrideChannel.getId())
                    .build());
        }

        return new ArrayList<>(recipientsByKey.values());
    }

    /** Convenience overload for callers that do not force a channel. */
    public List<AlertRecipient> resolve(AlertRule rule, String serviceType) {
        return resolve(rule, serviceType, null);
    }

    // rule bindings first; otherwise the per-type default channel; finally the earliest active channel of the type.
    // Public so the warning digest can add a no-@ placeholder for bound group channels that have no assignee.
    public Map<ChannelType, List<Long>> resolveRuleChannelIds(Long ruleId) {
        Map<ChannelType, List<Long>> channelIdsByType = new LinkedHashMap<>();
        for (RuleChannelView view : alertRuleChannelMapper.selectWithChannel(ruleId)) {
            ChannelType channelType = parseChannelType(view.getChannelType());
            if (channelType == null) {
                continue;
            }
            channelIdsByType.computeIfAbsent(channelType, k -> new ArrayList<>()).add(view.getChannelId());
        }
        if (!channelIdsByType.isEmpty()) {
            return channelIdsByType;
        }

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
        for (MessageChannel channel : firstByType.values()) {
            MessageChannel chosen = defaultByType.getOrDefault(channel.getChannelType(), channel);
            channelIdsByType.put(channel.getChannelType(), List.of(chosen.getId()));
        }
        if (channelIdsByType.isEmpty()) {
            log.warn("No active message channel configured, rule {} will not deliver", ruleId);
        }
        return channelIdsByType;
    }

    private void putRecipient(Map<String, AlertRecipient> recipientsByKey, Long assigneeId, String cnName,
                              String phone, String dingdingUsername, String email,
                              ChannelType channelType, Long messageChannelId) {
        recipientsByKey.put(assigneeId + ":" + channelType + ":" + messageChannelId, AlertRecipient.builder()
                .assigneeId(assigneeId)
                .cnName(cnName)
                .phone(phone)
                .dingdingUsername(dingdingUsername)
                .email(email)
                .channelType(channelType)
                .messageChannelId(messageChannelId)
                .build());
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
            log.warn("Unknown channel type in binding: {}", value);
            return null;
        }
    }
}
