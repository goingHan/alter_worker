package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class MessageChannelController {

    private final MessageChannelMapper messageChannelMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<MessageChannel> list(@RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "20") int size) {
        return messageChannelMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public MessageChannel get(@PathVariable Long id) {
        return messageChannelMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public MessageChannel create(@Valid @RequestBody MessageChannel entity) {
        messageChannelMapper.insert(entity);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(entity.getChannelType(), entity.getId());
        }
        auditLogService.log("CREATE", "MESSAGE_CHANNEL", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public MessageChannel update(@PathVariable Long id, @Valid @RequestBody MessageChannel entity) {
        entity.setId(id);
        messageChannelMapper.updateById(entity);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(entity.getChannelType(), id);
        }
        auditLogService.log("UPDATE", "MESSAGE_CHANNEL", id, "admin", null);
        return entity;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: mark a channel as the default of its type (only one default per type);
    // rules without explicit channel bindings fall back to these defaults
    @PutMapping("/{id}/default")
    public MessageChannel setDefault(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MessageChannel channel = messageChannelMapper.selectById(id);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found: " + id);
        }
        boolean isDefault = body.get("default") == null || Boolean.parseBoolean(String.valueOf(body.get("default")));
        if (isDefault) {
            clearOtherDefaults(channel.getChannelType(), id);
        }
        messageChannelMapper.update(null, new LambdaUpdateWrapper<MessageChannel>()
                .eq(MessageChannel::getId, id)
                .set(MessageChannel::getIsDefault, isDefault)
                .set(MessageChannel::getUpdatedAt, LocalDateTime.now()));
        auditLogService.log("UPDATE", "MESSAGE_CHANNEL_DEFAULT", id, "admin", null);
        channel.setIsDefault(isDefault);
        return channel;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        messageChannelMapper.deleteById(id);
        auditLogService.log("DELETE", "MESSAGE_CHANNEL", id, "admin", null);
    }

    private void clearOtherDefaults(ChannelType channelType, Long keepId) {
        if (channelType == null) {
            return;
        }
        messageChannelMapper.update(null, new LambdaUpdateWrapper<MessageChannel>()
                .eq(MessageChannel::getChannelType, channelType)
                .eq(MessageChannel::getDeleted, 0)
                .ne(MessageChannel::getId, keepId)
                .set(MessageChannel::getIsDefault, false)
                .set(MessageChannel::getUpdatedAt, LocalDateTime.now()));
    }
}
