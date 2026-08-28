# Worker HTTP 调试接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `alertworker-worker`(8082)加一个 `POST /api/v1/worker/dispatch` HTTP 接口,POST 一条 `AlertMessagePayload` 直接跑完整链路(状态流转→渲染→分发),绕过 MQ。

**Architecture:** 把 `AlertMessageConsumer.onMessage()` 中与 RabbitMQ 无关的核心逻辑抽到新的 `AlertMessageProcessor.process()`(返回 `ProcessingResult`),MQ 消费者只保留 ack/nack/deliveryTag 与重试 sleep;新的 `DebugDispatchController` 与消费者共用同一份 processor 逻辑。MQ 路径行为逐行保持不变。

**Tech Stack:** Java 17、Spring Boot 3.2.5、Spring Web、Lombok、MyBatis-Plus、Jinjava。

## Global Constraints

- Java 17;Spring Boot 3.2.5;模块仅 `alertworker-worker`,依赖仅新增自带的 spring-web(@RestController 已由 common 的 `spring-boot-starter-web` 传递提供,无需新依赖)。
- 端点为 `POST /api/v1/worker/dispatch`(非 debug 前缀)。
- AI 注解:分支 `dev_release/v0.0.1`、日期 `20260811`。新文件顶部 `#AI-CODE-FILE-...`;改动 consumer 方法上方 `#AI-CODE-FUNCTION-...`。
- 不改变 MQ 消费路径的既有行为(重试顺序、默认值、null 语义)。不可变性:复用现有 mappers,不新增共享可变状态。
- 本项目无测试基础设施(无 src/test、父 pom 无测试依赖),验证方式为编译 + 实跑 E2E。

---
---

### Task 1: 创建 ProcessingResult + AlertMessageProcessor

**Files:**
- Create: `alertworker-worker/src/main/java/com/alertworker/worker/service/ProcessingResult.java`
- Create: `alertworker-worker/src/main/java/com/alertworker/worker/service/AlertMessageProcessor.java`

**Interfaces:**
- Consumes: 既有 bean —— `WorkerAlertMessageService`(transitionToDelivered/transitionToWorkerSent/transitionToSuccess/transitionToFailed/incrementRetryCount/getRetryCount/writeToDeadLetter)、`AlertRuleMapper`、`AlertRuleTemplateMapper`、`MessageTemplateMapper`、`JinjavaTemplateService.render(String, Map)`、`MessageChannelDispatcher.dispatch(AlertRecipient, String)`。
- Produces: `AlertMessageProcessor.process(AlertMessagePayload) → ProcessingResult`;`ProcessingResult.ok(int,int)` / `ProcessingResult.failed(int,int,boolean,long,String)`;`success()/shouldRetry()/retryDelayMs()/successCount()/recipientCount()/error()`。Task 2、3 依赖这些签名。

- [ ] **Step 1: 创建 ProcessingResult**

```java
package com.alertworker.worker.service;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: processing result contract shared by MQ consumer and HTTP debug endpoint
public record ProcessingResult(
        boolean success,
        boolean shouldRetry,
        long retryDelayMs,
        int successCount,
        int recipientCount,
        String error) {

    public static ProcessingResult ok(int successCount, int recipientCount) {
        return new ProcessingResult(true, false, 0L, successCount, recipientCount, null);
    }

    public static ProcessingResult failed(int successCount, int recipientCount,
                                          boolean shouldRetry, long retryDelayMs, String error) {
        return new ProcessingResult(false, shouldRetry, retryDelayMs, successCount, recipientCount, error);
    }
}
```

- [ ] **Step 2: 创建 AlertMessageProcessor(核心逻辑从 AlertMessageConsumer 原样搬移)**

```java
package com.alertworker.worker.service;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.MessageSubStatus;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.worker.channel.MessageChannelDispatcher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: extract core processing from AlertMessageConsumer so MQ and HTTP debug paths share one implementation
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMessageProcessor {

    private final WorkerAlertMessageService workerAlertMessageService;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRuleTemplateMapper alertRuleTemplateMapper;
    private final MessageTemplateMapper messageTemplateMapper;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final MessageChannelDispatcher messageChannelDispatcher;

    public ProcessingResult process(AlertMessagePayload payload) {
        Long messageId = payload.getAlertMessageId();
        workerAlertMessageService.transitionToDelivered(messageId);

        AlertRule rule = null;
        int successCount = 0;
        try {
            rule = alertRuleMapper.selectById(payload.getAlertRuleId());
            if (rule == null || rule.getDeleted() == 1) {
                throw new IllegalStateException("Alert rule not found or deleted: " + payload.getAlertRuleId());
            }

            Map<ChannelType, MessageTemplate> templateMap = loadTemplates(rule.getId());
            MessageTemplate defaultTemplate = messageTemplateMapper.selectOne(
                    new LambdaQueryWrapper<MessageTemplate>()
                            .eq(MessageTemplate::getIsDefault, true)
                            .eq(MessageTemplate::getDeleted, 0)
            );

            boolean anySuccess = false;
            List<AlertRecipient> recipients = payload.getRecipients();
            for (AlertRecipient recipient : recipients) {
                MessageTemplate template = templateMap.get(recipient.getChannelType());
                if (template == null) {
                    template = defaultTemplate;
                }
                if (template == null) {
                    log.error("No template found for channelType={} and no default template", recipient.getChannelType());
                    continue;
                }

                Map<String, Object> ctx = new HashMap<>(payload.getContext());
                ctx.put("recipient", recipient);
                String rendered = jinjavaTemplateService.render(template.getContent(), ctx);

                workerAlertMessageService.transitionToWorkerSent(messageId);
                boolean sent = messageChannelDispatcher.dispatch(recipient, rendered);
                if (sent) {
                    anySuccess = true;
                    successCount++;
                }
            }

            if (anySuccess || recipients.isEmpty()) {
                workerAlertMessageService.transitionToSuccess(messageId);
                return ProcessingResult.ok(successCount, recipients.size());
            }
            throw new RuntimeException("All channel dispatches failed");
        } catch (Exception e) {
            return handleFailure(messageId, e, rule, successCount,
                    payload.getRecipients() == null ? 0 : payload.getRecipients().size());
        }
    }

    private Map<ChannelType, MessageTemplate> loadTemplates(Long ruleId) {
        Map<ChannelType, MessageTemplate> map = new HashMap<>();
        List<AlertRuleTemplate> ruleTemplates = alertRuleTemplateMapper.selectList(
                new LambdaQueryWrapper<AlertRuleTemplate>().eq(AlertRuleTemplate::getRuleId, ruleId)
        );
        for (AlertRuleTemplate rt : ruleTemplates) {
            MessageTemplate template = messageTemplateMapper.selectById(rt.getTemplateId());
            if (template != null && template.getDeleted() == 0) {
                map.put(rt.getChannelType(), template);
            }
        }
        return map;
    }

    private ProcessingResult handleFailure(Long messageId, Exception e, AlertRule rule,
                                           int successCount, int recipientCount) {
        log.error("Failed to process message {}, error: {}", messageId, e.getMessage(), e);
        int retryCount = workerAlertMessageService.getRetryCount(messageId);
        int maxRetry = (rule != null && rule.getRetryMax() != null) ? rule.getRetryMax() : 3;
        String backoffStr = (rule != null && rule.getRetryBackoff() != null) ? rule.getRetryBackoff() : "5s,10s,20s";

        workerAlertMessageService.incrementRetryCount(messageId);
        workerAlertMessageService.transitionToFailed(messageId, MessageSubStatus.worker_failed, e.getMessage());

        if (retryCount < maxRetry) {
            long delayMs = calculateBackoffDelay(retryCount, backoffStr);
            log.warn("Retrying message {} in {}ms, retryCount={}/{}", messageId, delayMs, retryCount + 1, maxRetry);
            return ProcessingResult.failed(successCount, recipientCount, true, delayMs, e.getMessage());
        }
        log.error("Message {} exhausted retries, sending to dead letter", messageId);
        workerAlertMessageService.writeToDeadLetter(messageId, e.getMessage());
        return ProcessingResult.failed(successCount, recipientCount, false, 0L, e.getMessage());
    }

    private long calculateBackoffDelay(int retryCount, String backoffStr) {
        String[] parts = backoffStr.split(",");
        if (retryCount < parts.length) {
            String unit = parts[retryCount].trim();
            if (unit.endsWith("s")) {
                return Long.parseLong(unit.substring(0, unit.length() - 1)) * 1000;
            }
        }
        return 5000;
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd /d/develop/java_works_space/AlertWorker && mvn -q -pl alertworker-worker -am compile`
Expected: `BUILD SUCCESS`,无编译错误。

- [ ] **Step 4: Commit**

```bash
git add alertworker-worker/src/main/java/com/alertworker/worker/service/ProcessingResult.java alertworker-worker/src/main/java/com/alertworker/worker/service/AlertMessageProcessor.java
git commit -m "feat(worker): extract AlertMessageProcessor from MQ consumer"
```

---
---

### Task 2: 改造 AlertMessageConsumer 委托给 processor

**Files:**
- Modify: `alertworker-worker/src/main/java/com/alertworker/worker/consumer/AlertMessageConsumer.java`(整文件重写)

**Interfaces:**
- Consumes: `AlertMessageProcessor.process(AlertMessagePayload) → ProcessingResult`(Task 1)。
- Produces: 对外行为不变——成功 `basicAck`;失败按 `shouldRetry` sleep 后 `basicNack(requeue=shouldRetry)`。MQ 路径与改造前逐行等价。

- [ ] **Step 1: 重写 AlertMessageConsumer**

```java
package com.alertworker.worker.consumer;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.worker.service.AlertMessageProcessor;
import com.alertworker.worker.service.ProcessingResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMessageConsumer {

    private final AlertMessageProcessor alertMessageProcessor;

    @RabbitListener(queues = "${alertworker.rabbitmq.queue}", ackMode = "MANUAL")
    public void onMessage(AlertMessagePayload payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long messageId = payload.getAlertMessageId();
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260811: delegate core processing to AlertMessageProcessor; keep only MQ ack/nack + retry sleep
        log.info("Received message from MQ, messageId={}", messageId);
        ProcessingResult result = alertMessageProcessor.process(payload);
        if (result.success()) {
            channel.basicAck(deliveryTag, false);
        } else {
            log.warn("Message {} processing failed: {}, shouldRetry={}, retryDelayMs={}",
                    messageId, result.error(), result.shouldRetry(), result.retryDelayMs());
            if (result.shouldRetry()) {
                try {
                    Thread.sleep(result.retryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            channel.basicNack(deliveryTag, false, result.shouldRetry());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd /d/develop/java_works_space/AlertWorker && mvn -q -pl alertworker-worker -am compile`
Expected: `BUILD SUCCESS`(旧的 mapper/Jinjava/dispatcher 依赖已从 consumer 移除,不再引用)。

- [ ] **Step 3: Commit**

```bash
git add alertworker-worker/src/main/java/com/alertworker/worker/consumer/AlertMessageConsumer.java
git commit -m "refactor(worker): delegate MQ processing to AlertMessageProcessor"
```

---
---

### Task 3: 新增 DebugDispatchController

**Files:**
- Create: `alertworker-worker/src/main/java/com/alertworker/worker/controller/DebugDispatchController.java`

**Interfaces:**
- Consumes: `AlertMessageProcessor.process(AlertMessagePayload) → ProcessingResult`(Task 1)、`AlertMessagePayload` / `AlertRecipient`(common DTO)。
- Produces: `POST /api/v1/worker/dispatch`。200 `{messageId, result:success, successCount, recipientCount}`;500 `{messageId, result:failed, successCount, recipientCount, error}`;400 `{error}`。

- [ ] **Step 1: 创建 DebugDispatchController**

```java
package com.alertworker.worker.controller;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.worker.service.AlertMessageProcessor;
import com.alertworker.worker.service.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: HTTP debug endpoint to run the worker pipeline directly, bypassing RabbitMQ
@Slf4j
@RestController
@RequestMapping("/api/v1/worker")
@RequiredArgsConstructor
public class DebugDispatchController {

    private final AlertMessageProcessor alertMessageProcessor;

    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatch(@RequestBody(required = false) AlertMessagePayload payload) {
        if (payload == null) {
            return error(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (payload.getAlertMessageId() == null) {
            return error(HttpStatus.BAD_REQUEST, "alertMessageId is required");
        }
        if (payload.getAlertRuleId() == null) {
            return error(HttpStatus.BAD_REQUEST, "alertRuleId is required");
        }
        if (payload.getRecipients() == null) {
            return error(HttpStatus.BAD_REQUEST, "recipients must not be null (use [] for empty)");
        }

        ProcessingResult result = alertMessageProcessor.process(payload);
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", payload.getAlertMessageId());
        body.put("result", result.success() ? "success" : "failed");
        body.put("successCount", result.successCount());
        body.put("recipientCount", result.recipientCount());
        if (result.error() != null) {
            body.put("error", result.error());
        }
        return result.success()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd /d/develop/java_works_space/AlertWorker && mvn -q -pl alertworker-worker -am compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add alertworker-worker/src/main/java/com/alertworker/worker/controller/DebugDispatchController.java
git commit -m "feat(worker): add POST /api/v1/worker/dispatch debug endpoint"
```

---
---

### Task 4: 实跑 E2E 验证

**Files:** 无(仅验证)。

**Interfaces:** 复用 Task 1-3 产物。

- [ ] **Step 1: 确保 DB 有测试数据(最小集,来自 design doc 第 5 节)**

若本机有 `psql`:
```bash
psql "postgresql://airflow:airflow@172.168.1.238:25432/alertworker" <<'SQL'
INSERT INTO alert_rule (id, name, alert_type, match_key, match_value, retry_max, retry_backoff)
VALUES (1, 'cpu-high', 'PROMETHEUS', 'alertname', 'CPUHigh', 3, '5s,10s,20s')
ON CONFLICT (id) DO NOTHING;
INSERT INTO message_template (id, name, channel_type, content, is_default)
VALUES (1, 'dingding-default', 'DINGDING', '【告警】{{ title }}\n{{ content }}', true)
ON CONFLICT (id) DO NOTHING;
INSERT INTO alert_rule_template (rule_id, template_id, channel_type)
VALUES (1, 1, 'DINGDING') ON CONFLICT DO NOTHING;
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (1, 'dingding-test', 'DINGDING',
        '{"webhookUrl":"http://127.0.0.1:9999/hook","msgType":"text"}')
ON CONFLICT (id) DO NOTHING;
INSERT INTO alert_message (api_key_id, alert_rule_id, payload, status, sub_status, retry_count)
VALUES (1, 1, '{"service_type":"e2e"}', 'GW_SENT', NULL, 0);
SQL
```
无 psql 时改用 DataManager API 或跳过真实投递,只做 Step 2 的 400 校验(证明接口可达)。

- [ ] **Step 2: 起 worker**

```bash
cd /d/develop/java_works_space/AlertWorker/alertworker-worker && mvn -q spring-boot:run
```
Expected: 日志出现 `Started WorkerApplication`、Tomcat 8082。

- [ ] **Step 3: 校验 400 分支(不依赖 DB 数据)**

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/api/v1/worker/dispatch \
  -H 'Content-Type: application/json' -d '{}'
```
Expected: `400`。

- [ ] **Step 4: 校验完整链路(有 DB 数据时)**

```bash
MID=$(psql "postgresql://airflow:airflow@172.168.1.238:25432/alertworker" -tA -c "SELECT id FROM alert_message WHERE alert_rule_id=1 ORDER BY id DESC LIMIT 1")
curl -s -X POST http://localhost:8082/api/v1/worker/dispatch \
  -H 'Content-Type: application/json' \
  -d "{\"alertMessageId\":$MID,\"alertRuleId\":1,\"context\":{\"title\":\"CPU 过高\",\"content\":\"node-01 CPU > 90%\"},\"recipients\":[{\"assigneeId\":1,\"cnName\":\"张三\",\"channelType\":\"DINGDING\",\"messageChannelId\":1}]}"
```
Expected: 200 + `{"messageId":...,"result":"success","successCount":1,"recipientCount":1}`;worker 日志有渲染/分发;`SELECT status FROM alert_message WHERE id=$MID` 为 `success`;钉钉 mock(127.0.0.1:9999)收到消息。

- [ ] **Step 5: 停 worker,清理**

```bash
# 停掉 spring-boot:run 进程,确认 8082 释放
```

---
---

## Self-Review

- **Spec coverage:** 目标✓(Task 3 端点)、组件划分 4 文件✓(Task 1/2/3)、接口契约 200/500/400✓(Task 3)、错误处理✓(controller 400;processor 内部 retry/死信;HTTP 不 sleep)、验证方案✓(Task 4)、AI 注解✓(各文件含 `dev_release/v0.0.1-20260811`)。"不做"项——无开关、无鉴权、不改 common、不自动建 alert_message——均未越界。
- **Placeholder scan:** 无 TBD/TODO;每个 Task 的代码步骤含完整代码;Task 4 的 psql 缺失分支有明确降级说明。
- **Type consistency:** `ProcessingResult.ok/failed` 工厂与 record 构造一致;consumer/controller 均调 `process(payload)` 返回 `ProcessingResult`;`success()/shouldRetry()/retryDelayMs()/successCount()/recipientCount()/error()` 为 record 自动生成访问器,三处引用一致。URL 全部为 `/api/v1/worker/dispatch`。
