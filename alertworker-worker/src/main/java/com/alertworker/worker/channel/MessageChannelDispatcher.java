package com.alertworker.worker.channel;

import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.MessageChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChannelDispatcher {

    private final MessageChannelMapper messageChannelMapper;
    private final DingDingChannelHandler dingDingChannelHandler;
    private final EmailChannelHandler emailChannelHandler;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260907: batch all recipients sharing the same channel into a single call.
    // Only messages spread across one channel (e.g. one DingDing robot) so recipients are @-ed together in a single message.
    public boolean dispatch(List<AlertRecipient> recipients, String renderedContent) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("dispatch called with empty recipients");
            return false;
        }

        Long channelId = recipients.get(0).getMessageChannelId();
        MessageChannel channel = messageChannelMapper.selectById(channelId);
        if (channel == null || channel.getDeleted() == 1) {
            log.error("Message channel not found or deleted: {}", channelId);
            return false;
        }

        if (channel.getChannelType() == ChannelType.DINGDING) {
            return dingDingChannelHandler.send(channel, recipients, renderedContent);
        } else if (channel.getChannelType() == ChannelType.EMAIL) {
            boolean allSuccess = true;
            for (AlertRecipient recipient : recipients) {
                allSuccess &= emailChannelHandler.send(channel, recipient, renderedContent);
            }
            return allSuccess;
        }

        log.error("Unsupported channel type: {}", channel.getChannelType());
        return false;
    }
}
