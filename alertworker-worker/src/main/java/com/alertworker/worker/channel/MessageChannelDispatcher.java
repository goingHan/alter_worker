package com.alertworker.worker.channel;

import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.MessageChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChannelDispatcher {

    private final MessageChannelMapper messageChannelMapper;
    private final DingDingChannelHandler dingDingChannelHandler;
    private final EmailChannelHandler emailChannelHandler;

    public boolean dispatch(AlertRecipient recipient, String renderedContent) {
        MessageChannel channel = messageChannelMapper.selectById(recipient.getMessageChannelId());
        if (channel == null || channel.getDeleted() == 1) {
            log.error("Message channel not found or deleted: {}", recipient.getMessageChannelId());
            return false;
        }

        if (channel.getChannelType() == ChannelType.DINGDING) {
            return dingDingChannelHandler.send(channel, recipient, renderedContent);
        } else if (channel.getChannelType() == ChannelType.EMAIL) {
            return emailChannelHandler.send(channel, recipient, renderedContent);
        }

        log.error("Unsupported channel type: {}", channel.getChannelType());
        return false;
    }
}
