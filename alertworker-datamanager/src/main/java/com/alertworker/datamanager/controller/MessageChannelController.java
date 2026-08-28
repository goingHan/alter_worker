package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        auditLogService.log("CREATE", "MESSAGE_CHANNEL", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public MessageChannel update(@PathVariable Long id, @Valid @RequestBody MessageChannel entity) {
        entity.setId(id);
        messageChannelMapper.updateById(entity);
        auditLogService.log("UPDATE", "MESSAGE_CHANNEL", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        messageChannelMapper.deleteById(id);
        auditLogService.log("DELETE", "MESSAGE_CHANNEL", id, "admin", null);
    }
}
