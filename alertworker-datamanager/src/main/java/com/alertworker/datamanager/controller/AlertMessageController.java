package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.DeadLetterMessage;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.DeadLetterMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertMessageController {

    private final AlertMessageMapper alertMessageMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;

    @GetMapping("/history")
    public Page<AlertMessage> history(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "20") int size) {
        return alertMessageMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AlertMessage>().orderByDesc(AlertMessage::getCreatedAt)
        );
    }

    @GetMapping("/history/{id}")
    public AlertMessage detail(@PathVariable Long id) {
        return alertMessageMapper.selectById(id);
    }

    @GetMapping("/delivery-logs")
    public Page<AlertMessage> deliveryLogs(@RequestParam(defaultValue = "1") int current,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String status) {
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260804: explicit type arg so orderByDesc(SFunction) infers correctly
        LambdaQueryWrapper<AlertMessage> wrapper = new LambdaQueryWrapper<AlertMessage>()
                .orderByDesc(AlertMessage::getCreatedAt);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AlertMessage::getStatus, MessageStatus.valueOf(status));
        }
        return alertMessageMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @GetMapping("/failed")
    public Page<AlertMessage> failedMessages(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "20") int size) {
        return alertMessageMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AlertMessage>()
                        .eq(AlertMessage::getStatus, MessageStatus.FAILED)
                        .orderByDesc(AlertMessage::getCreatedAt)
        );
    }

    @GetMapping("/dead-letters")
    public Page<DeadLetterMessage> deadLetters(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "20") int size) {
        return deadLetterMessageMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<DeadLetterMessage>().orderByDesc(DeadLetterMessage::getCreatedAt)
        );
    }
}
