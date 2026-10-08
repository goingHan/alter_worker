# AlertWorker Worker HTTP 调试接口设计

#AI-CODE-FILE-dev_release/v0.0.1-20260810: worker HTTP 调试接口设计文档(POST 一条 AlertMessagePayload 直跑完整链路,绕过 MQ)

## 背景与目标

`alertworker-worker`(端口 8082)目前**没有**任何 HTTP 接口:全部 `@RestController` 都在 datamanager / gateway,worker 唯一的入口是 RabbitMQ 消费者(`AlertMessageConsumer`)。Tomcat 之所以在 8082 起来,只是因为 `alertworker-common` 传递引入了 `spring-boot-starter-web`,实际没有映射任何 endpoint。

现状想独立测 worker 只有一条路:按 `docs/design/worker-standalone-test.md` 往 `alertworker.queue` 投一条 `AlertMessagePayload` JSON——这条路**依然经过 MQ**(只是绕过 gateway)。

目标:给 worker 增加一个 HTTP 调试接口,POST 一条 `AlertMessagePayload` 直接跑完整链路(和 MQ 消费行为一致),完全不经过 MQ,方便本地/测试环境快速验证「查规则/模板 → Jinjava 渲染 → 通道分发 → DB 状态流转」整条链路。

## 范围

### 做
- 抽取核心处理逻辑为可复用服务,HTTP 与 MQ 共用同一份逻辑。
- 新增一个调试用 `POST` 接口。
- 完善接口的错误返回(400 参数校验 / 500 业务失败)。

### 不做
- 不加接口开关配置(YAGNI):这是调试接口,路径写在 `/api/v1/worker/` 下已足够明显。
- 不在 worker 上加认证/鉴权:worker 本就不对外暴露 HTTP,调试接口仅限开发环境使用。
- 不改 gateway / datamanager / common(除复用现有 DTO 外)。
- 不做自动创建 `alert_message` 记录:和现有 MQ 独立测试一致,前置数据由调用方准备。

## 架构与组件

核心思路:把 `AlertMessageConsumer.onMessage()` 里「与 RabbitMQ 无关」的逻辑整体抽到新的 `AlertMessageProcessor`,MQ 消费者与 HTTP controller 都调用它。MQ 专属的 ack/nack/deliveryTag 与重试 sleep 留在消费者。

### 组件清单(4 个文件)

| 文件 | 类型 | 职责 |
|---|---|---|
| `worker/service/AlertMessageProcessor.java` | 新增 | 核心处理:状态流转、查规则/模板(含默认兜底)、Jinjava 渲染、通道分发、失败 retry 记账 + 死信落库 |
| `worker/service/ProcessingResult.java` | 新增 | processor 与调用方的契约(小 record) |
| `worker/controller/DebugDispatchController.java` | 新增 | `POST /api/v1/worker/dispatch`,参数校验 fail-fast,调 processor,按结果返回 |
| `worker/consumer/AlertMessageConsumer.java` | 改造 | 只保留 RabbitMQ 专属逻辑,核心逻辑委托给 processor,MQ 路径行为不变 |

### AlertMessageProcessor 依赖
`WorkerAlertMessageService`(状态流转/retry 记账/死信)、`AlertRuleMapper`、`AlertRuleTemplateMapper`、`MessageTemplateMapper`、`JinjavaTemplateService`、`MessageChannelDispatcher`——全部现有 bean,无新依赖。

## 处理流程(抽取后)

`process(AlertMessagePayload) → ProcessingResult`:

1. `transitionToDelivered(messageId)`(message 不存在时静默跳过,同现状)。
2. 查 `alert_rule`;null 或 `deleted=1` → 失败(同现状的 IllegalStateException)。
3. 查该规则关联模板 `templateMap`(按 channelType)+ 全局默认模板(`is_default=true, deleted=0`)。
4. 遍历 `recipients`:
   - 取该 channelType 模板,没有则回退默认模板;仍没有 → 记日志跳过该接收人(同现状)。
   - `ctx = new HashMap<>(payload.context)` + `recipient` → Jinjava 渲染。
   - `transitionToWorkerSent(messageId)` → `messageChannelDispatcher.dispatch(recipient, rendered)`。
   - 记录 successCount / anySuccess。
5. `anySuccess || recipients.isEmpty()` → `transitionToSuccess`;否则失败。
6. 失败时(与现状 handleFailure 同序):`incrementRetryCount` → `transitionToFailed(worker_failed)` → 若 `retryCount < maxRetry` 记 `shouldRetry=true + retryDelayMs`(按规则 `retry_backoff`,如 `5s,10s,20s`),否则 `writeToDeadLetter` + `shouldRetry=false`。

## 接口契约

### `POST /api/v1/worker/dispatch`

Request body:`AlertMessagePayload`
```json
{
  "alertMessageId": 42,
  "alertRuleId": 1,
  "context": { "service_type": "e2e", "title": "CPU 使用率过高", "content": "node-01 CPU > 90%" },
  "recipients": [ { "assigneeId": 1, "cnName": "张三", "channelType": "DINGDING", "messageChannelId": 1 } ]
}
```

| 状态码 | 场景 | Response body |
|---|---|---|
| 200 | 处理成功 | `{ "messageId": 42, "result": "success", "successCount": 1, "recipientCount": 1 }` |
| 500 | 业务失败(规则缺失/全部分发失败,已置 FAILED/死信) | `{ "messageId": 42, "result": "failed", "successCount": 0, "recipientCount": 1, "error": "..." }` |
| 400 | 参数校验失败 | `{ "error": "alertMessageId is required" }` |

校验规则:body 非空;`alertMessageId`、`alertRuleId` 非空;`recipients` 可为空数组(与现状一致,空则 success)。

## 错误处理

- **参数校验**:在 controller 边界 fail-fast,返回 400,不落 DB、不置状态。
- **业务失败**:processor 内部完成 `FAILED/worker_failed` + retry 记账 + 死信落库(与 MQ 路径一致),然后返回 failed 结果,HTTP 层返回 500。controller 不 sleep、不重试。
- **MQ 路径不变**:消费者在失败时仍按 `shouldRetry` 决定 `Thread.sleep(retryDelayMs)` + `basicNack(requeue=shouldRetry)`;成功 `basicAck`。

## 验证方案

1. 起 worker(`cd alertworker-worker && mvn spring-boot:run`),8082 起来。
2. 备好 DB 最小集(规则/模板/通道/一条 `alert_message`,见 `docs/design/worker-standalone-test.md` 第 5 节)。
3. `curl -X POST localhost:8082/api/v1/worker/dispatch -H 'Content-Type: application/json' -d '{...}'`,观察:
   - 返回 200 + `result: success`;
   - worker 日志出现渲染与分发;
   - 目标通道(钉钉 mock 或真实 webhook)收到消息;
   - `SELECT status, sub_status, retry_count FROM alert_message WHERE id = 42;` 到 `success`。
4. 对照:同一条报文走 MQ 投递,DB 状态与分发结果一致(回归验证 consumer 改造未破坏 MQ 路径)。

## 代码注解

按仓库 `AI Code Annotation` 规则:
- 新增 3 个文件:文件顶部加 `#AI-CODE-FILE-dev_release/v0.0.1-20260810: <描述>`。
- 改造 `AlertMessageConsumer`:被改动的方法上方加 `#AI-CODE-FUNCTION-dev_release/v0.0.1-20260810: <描述>`。

## 文件变更清单

- [ ] 新增 `alertworker-worker/src/main/java/com/alertworker/worker/service/AlertMessageProcessor.java`
- [ ] 新增 `alertworker-worker/src/main/java/com/alertworker/worker/service/ProcessingResult.java`
- [ ] 新增 `alertworker-worker/src/main/java/com/alertworker/worker/controller/DebugDispatchController.java`
- [ ] 改造 `alertworker-worker/src/main/java/com/alertworker/worker/consumer/AlertMessageConsumer.java`
