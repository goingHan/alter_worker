package com.alertworker.gateway.controller;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.ApiKey;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.ApiKeyMapper;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.util.MessageNoGenerator;
import com.alertworker.gateway.service.AlertIngestService;
import com.alertworker.gateway.service.TestAlertDataGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260916: test-alert endpoint — random payloads forced onto the selected rules
// (no matcher) or a manual payload through the real matcher, both routed through the normal MQ pipeline
@Slf4j
@RestController
@RequestMapping("/api/v1/test-alerts")
@RequiredArgsConstructor
public class TestAlertController {

    private final AlertRuleMapper alertRuleMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final MessageChannelMapper messageChannelMapper;
    private final AlertIngestService alertIngestService;
    private final TestAlertDataGenerator testAlertDataGenerator;

    @PostMapping
    public ResponseEntity<?> test(@RequestBody TestAlertRequest request) {
        boolean payloadMode = StringUtils.hasText(request.getRawPayload());

        MessageChannel overrideChannel = null;
        if (request.getChannelId() != null) {
            MessageChannel channel = messageChannelMapper.selectById(request.getChannelId());
            if (channel == null || channel.getDeleted() == 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "通道不存在: " + request.getChannelId()));
            }
            overrideChannel = channel;
        }

        ApiKey apiKey = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getDeleted, 0)
                .orderByAsc(ApiKey::getId)
                .last("LIMIT 1"));
        if (apiKey == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请先在「API 密钥」页面创建至少一个密钥"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        if (payloadMode) {
            AlertType alertType = request.getAlertType() != null
                    ? request.getAlertType()
                    : resolveDefaultAlertType(request.getRuleIds());
            AlertIngestService.IngestResult result = alertIngestService.ingest(
                    apiKey.getId(), alertType, request.getRawPayload(), MessageNoGenerator.generate(), null, overrideChannel);
            results.add(toResult(null, result));
        } else {
            if (CollectionUtils.isEmpty(request.getRuleIds())) {
                return ResponseEntity.badRequest().body(Map.of("message", "请至少选择一条规则"));
            }
            for (Long ruleId : request.getRuleIds()) {
                AlertRule rule = alertRuleMapper.selectById(ruleId);
                if (rule == null || rule.getDeleted() == 1) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ruleId", ruleId);
                    row.put("success", false);
                    row.put("recipientCount", 0);
                    row.put("error", "规则不存在或已删除");
                    results.add(row);
                    continue;
                }
                String rawPayload = testAlertDataGenerator.generate(rule);
                AlertIngestService.IngestResult result = alertIngestService.ingest(
                        apiKey.getId(), rule.getAlertType(), rawPayload, MessageNoGenerator.generate(), rule, overrideChannel);
                results.add(toResult(rule, result));
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("mode", payloadMode ? "payload" : "auto");
        body.put("message", "已提交 " + results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count() + " 条测试告警");
        body.put("results", results);
        return ResponseEntity.ok(body);
    }

    private AlertType resolveDefaultAlertType(List<Long> ruleIds) {
        if (!CollectionUtils.isEmpty(ruleIds)) {
            AlertRule rule = alertRuleMapper.selectById(ruleIds.get(0));
            if (rule != null && rule.getAlertType() != null) {
                return rule.getAlertType();
            }
        }
        return AlertType.JSON;
    }

    private Map<String, Object> toResult(AlertRule rule, AlertIngestService.IngestResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", rule != null ? rule.getId() : null);
        row.put("ruleName", rule != null ? rule.getName() : null);
        row.put("matchedRuleId", result.rule() != null ? result.rule().getId() : null);
        row.put("matchedRuleName", result.rule() != null ? result.rule().getName() : null);
        row.put("messageNo", result.messageNo());
        row.put("recordId", result.recordId());
        row.put("recipientCount", result.recipientCount());
        row.put("success", result.success());
        row.put("error", result.error());
        return row;
    }

    @Data
    public static class TestAlertRequest {
        private List<Long> ruleIds;
        private Long channelId;
        private String mode;
        private AlertType alertType;
        private String rawPayload;
    }
}
