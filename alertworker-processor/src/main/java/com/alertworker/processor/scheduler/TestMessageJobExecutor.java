package com.alertworker.processor.scheduler;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.enums.MessageKind;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.messaging.AlertMessageProducer;
import com.alertworker.common.scheduler.JobExecutor;
import com.alertworker.common.service.AlertMessageRecordService;
import com.alertworker.common.util.MessageNoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Periodic "channel is alive" test job ({@code jobType = TEST_MESSAGE}).
 *
 * <p>For every target channel it creates one {@code alert_message} row ({@code message_type = TEST}) and enqueues
 * a TEST payload through the common RabbitMQ producer, so the Worker renders the built-in test template and
 * dispatches through the normal channel path (retry + dead-letter included). It never resolves alert-rule
 * recipients and never calls the gateway.
 *
 * <p>Target channels come from {@code config_json} {@code {"channelIds": [..]}}; empty or absent means ALL active
 * channels ({@code message_channel.deleted = 0}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TestMessageJobExecutor implements JobExecutor {

    public static final String JOB_TYPE = "TEST_MESSAGE";
    private static final String MESSAGE_NO_PREFIX = "TEST-";
    private static final String TEST_RECIPIENT_NAME = "测试接收人";

    private final AlertMessageRecordService alertMessageRecordService;
    private final AlertMessageProducer alertMessageProducer;
    private final MessageChannelMapper messageChannelMapper;
    private final AlertMessageMapper alertMessageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void execute(ScheduledJob job) {
        List<Long> channelIds = parseChannelIds(job);
        List<MessageChannel> channels = resolveChannels(channelIds);
        if (channels.isEmpty()) {
            log.warn("Scheduled TEST_MESSAGE job id={} has no target channels, nothing to send", job.getId());
            return;
        }
        for (MessageChannel channel : channels) {
            sendTestForChannel(channel);
        }
        log.info("Scheduled TEST_MESSAGE job id={} enqueued {} test message(s)", job.getId(), channels.size());
    }

    private void sendTestForChannel(MessageChannel channel) {
        String messageNo = MESSAGE_NO_PREFIX + MessageNoGenerator.generate();
        Map<String, Object> context = Map.of("channelName", channel.getName());

        AlertMessage message = alertMessageRecordService.create(null, null, context, messageNo,
                null, channel.getName());
        // create() does not set messageType, so the column falls back to the DB default 'ALERT' — wrong for a
        // periodic channel test. Persist messageType='TEST' with a targeted update.
        alertMessageMapper.update(null, new LambdaUpdateWrapper<AlertMessage>()
                .eq(AlertMessage::getId, message.getId())
                .set(AlertMessage::getMessageType, "TEST"));
        alertMessageRecordService.transitionToGwSent(message.getId());

        AlertMessagePayload payload = AlertMessagePayload.builder()
                .kind(MessageKind.TEST)
                .alertMessageId(message.getId())
                .messageNo(messageNo)
                .alertRuleId(null)
                .context(Map.of("channelName", channel.getName()))
                .recipients(List.of(AlertRecipient.builder()
                        .channelType(channel.getChannelType())
                        .messageChannelId(channel.getId())
                        .cnName(TEST_RECIPIENT_NAME)
                        .build()))
                .build();
        alertMessageProducer.send(payload);
        log.info("Enqueued TEST message no={} for channel id={} ({})",
                messageNo, channel.getId(), channel.getName());
    }

    private List<MessageChannel> resolveChannels(List<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return messageChannelMapper.selectList(new LambdaQueryWrapper<MessageChannel>()
                    .eq(MessageChannel::getDeleted, 0));
        }
        List<MessageChannel> channels = new ArrayList<>();
        for (Long id : channelIds) {
            MessageChannel channel = messageChannelMapper.selectById(id);
            if (channel == null) {
                log.warn("Scheduled TEST_MESSAGE references missing message_channel id={}, skipping", id);
                continue;
            }
            channels.add(channel);
        }
        return channels;
    }

    private List<Long> parseChannelIds(ScheduledJob job) {
        if (job.getConfigJson() == null || job.getConfigJson().isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(job.getConfigJson());
            JsonNode channelIds = root.get("channelIds");
            if (channelIds == null || !channelIds.isArray()) {
                return List.of();
            }
            return collectIds(channelIds);
        } catch (JsonProcessingException e) {
            log.warn("Scheduled TEST_MESSAGE job id={} has invalid configJson, falling back to all active channels",
                    job.getId(), e);
            return List.of();
        }
    }

    private List<Long> collectIds(JsonNode channelIds) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode idNode : channelIds) {
            if (idNode.isNumber()) {
                ids.add(idNode.asLong());
            }
        }
        return ids;
    }
}
