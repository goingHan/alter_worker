package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.DeadLetterMessage;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.DeadLetterMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertMessageController {

    private final AlertMessageMapper alertMessageMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;

    @GetMapping("/recent")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "10") int size) {
        List<AlertMessage> messages = alertMessageMapper.selectList(
                new LambdaQueryWrapper<AlertMessage>()
                        .ge(AlertMessage::getCreatedAt, LocalDateTime.now().minusHours(24))
                        .orderByDesc(AlertMessage::getCreatedAt)
                        .last("LIMIT " + Math.min(size, 100))
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (AlertMessage message : messages) {
            result.add(recentView(message));
        }
        return result;
    }

    private Map<String, Object> recentView(AlertMessage message) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", message.getId());
        view.put("messageNo", message.getMessageNo());
        view.put("alertRuleId", message.getAlertRuleId());
        view.put("status", message.getStatus());
        view.put("subStatus", message.getSubStatus());
        view.put("retryCount", message.getRetryCount());
        view.put("createdAt", message.getCreatedAt());
        view.put("summary", summarize(message.getPayload()));
        view.put("fullContent", message.getPayload());
        view.put("totalMs", totalMs(message));
        return view;
    }

    private String summarize(String payload) {
        if (payload == null) {
            return "";
        }
        String cleaned = payload.length() > 200 ? payload.substring(0, 200) + "..." : payload;
        return cleaned;
    }

    private Long totalMs(AlertMessage message) {
        LocalDateTime start = message.getReceivedAt() != null ? message.getReceivedAt() : message.getCreatedAt();
        LocalDateTime end = message.getFinishedAt();
        if (end == null) {
            return null;
        }
        return Math.max(0, Duration.between(start, end).toMillis());
    }

    @GetMapping("/history")
    public IPage<Map<String, Object>> history(@RequestParam(defaultValue = "1") int current,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String messageNo,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) Boolean inDeadLetter,
                                              @RequestParam(required = false) String ruleName,
                                              @RequestParam(required = false) String channelName,
                                              // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: unified history type
                                              // filter (ALERT / TEST / REPORT)
                                              @RequestParam(required = false) String messageType) {
        LocalDateTime start = parseStart(startDate);
        LocalDateTime end = parseEnd(endDate);
        return alertMessageMapper.searchHistory(
                new Page<>(current, size),
                messageNo, status, start, end, inDeadLetter, ruleName, channelName, messageType);
    }

    private LocalDateTime parseStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replace(' ', 'T');
        return v.length() <= 10 ? LocalDate.parse(v).atStartOfDay() : LocalDateTime.parse(v);
    }

    private LocalDateTime parseEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replace(' ', 'T');
        if (v.length() <= 10) {
            return LocalDate.parse(v).plusDays(1).atStartOfDay().minusNanos(1);
        }
        return LocalDateTime.parse(v);
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
        Page<AlertMessage> page = alertMessageMapper.selectPage(new Page<>(current, size), wrapper);
        page.getRecords().forEach(m -> m.setRawPayload(null));
        return page;
    }

    @GetMapping("/failed")
    public Page<AlertMessage> failedMessages(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "20") int size) {
        Page<AlertMessage> page = alertMessageMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AlertMessage>()
                        .eq(AlertMessage::getStatus, MessageStatus.FAILED)
                        .orderByDesc(AlertMessage::getCreatedAt)
        );
        page.getRecords().forEach(m -> m.setRawPayload(null));
        return page;
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
