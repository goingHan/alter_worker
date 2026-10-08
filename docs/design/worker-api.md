# AlertWorker Worker HTTP API 文档

#AI-CODE-FILE-dev_release/v0.0.1-20260811: worker HTTP 直调接口文档（绕过 RabbitMQ 触发 worker 流水线）

> 目的：**直接发 HTTP 请求给 `alertworker-worker`（端口 8082）**，绕过 gateway 和 RabbitMQ，触发 worker 的「查规则/模板/通道 → Jinjava 渲染 → 钉钉/邮件分发 → DB 状态流转」整条链路。适合调试、验证通道配置和手工重放。

---

## 1. 服务概览

| 项 | 值 |
|---|---|
| Base URL | `http://localhost:8082`（生产环境按部署地址替换） |
| 接口前缀 | `/api/v1/worker` |
| 认证 | 无（**不校验 API Key**，请勿暴露到公网） |
| 数据流 | HTTP 直调，**不经过** RabbitMQ |

> 与 gateway 接口的区别：worker 不做 API Key 校验、不做告警解析、不做规则匹配、**不生成 `alert_message` 记录**、不决定接收人——这些都需要调用方自己准备好，直接以 `AlertMessagePayload` 形式传入。

---

## 2. 接口：触发分发

### `POST /api/v1/worker/dispatch`

**Headers**：
- `Content-Type: application/json`

### 请求体（`AlertMessagePayload`）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `alertMessageId` | Long | **是** | `alert_message.id`，必须在 DB 中存在（worker 依赖它做状态流转与死信落库） |
| `alertRuleId` | Long | **是** | `alert_rule.id`，必须在 DB 中存在且 `deleted=0` |
| `context` | Map | 否 | 原始告警上下文，供 Jinjava 模板渲染（如 `{{ title }}`、`{{ content }}`、`{{ service_type }}`） |
| `recipients` | List | **是** | 接收人列表（`AlertRecipient[]`），空数组 `[]` 也可以 |

`recipients[]` 每个元素（`AlertRecipient`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `assigneeId` | Long | 接收人 id（`assignee.id`，worker 不强校验） |
| `cnName` | String | 中文名，模板里可用 `{{ recipient.cnName }}` |
| `phone` | String | 手机号，模板里可用 `{{ recipient.phone }}` |
| `dingdingUsername` | String | 钉钉用户名，模板里可用 `{{ recipient.dingdingUsername }}` |
| `channelType` | String | `DINGDING` 或 `EMAIL`，决定用哪个模板和哪个通道处理器 |
| `messageChannelId` | Long | 通道 id（`message_channel.id`），worker 用它取 `config_json` 里的 webhook / SMTP 配置 |

### 请求示例（钉钉 + 邮件两个接收人）

```json
{
  "alertMessageId": 42,
  "alertRuleId": 1,
  "context": {
    "service_type": "e2e",
    "title": "CPU 使用率过高",
    "content": "node-01 CPU 使用率超过 90%"
  },
  "recipients": [
    {
      "assigneeId": 1,
      "cnName": "张三",
      "phone": "13800138000",
      "dingdingUsername": "zhangsan",
      "channelType": "DINGDING",
      "messageChannelId": 1
    },
    {
      "assigneeId": 2,
      "cnName": "李四",
      "phone": "lisi@example.com",
      "dingdingUsername": "lisi",
      "channelType": "EMAIL",
      "messageChannelId": 2
    }
  ]
}
```

### 响应

**成功（200）**——至少一个接收人分发成功（或接收人列表为空）：

```json
{
  "messageId": 42,
  "result": "success",
  "successCount": 2,
  "recipientCount": 2
}
```

**参数校验失败（400）**——`body` 缺失 / `alertMessageId` 缺失 / `alertRuleId` 缺失 / `recipients` 为 null：

```json
{
  "error": "alertMessageId is required"
}
```

**分发失败（500）**——全部接收人分发失败或处理异常（如规则不存在、模板缺失）：

```json
{
  "messageId": 42,
  "result": "failed",
  "successCount": 0,
  "recipientCount": 2,
  "error": "All channel dispatches failed"
}
```

| 响应字段 | 说明 |
|---|---|
| `messageId` | 回显 `alertMessageId` |
| `result` | `success` / `failed` |
| `successCount` | 实际分发成功的接收人数 |
| `recipientCount` | 传入的接收人总数 |
| `error` | 失败原因（仅失败时返回） |

---

## 3. 前置条件（DB 数据准备）

worker 处理时会按 `alertRuleId` 重新查库，调用前必须已存在以下数据（通常由 DataManager 创建）：

1. **`alert_message`**：必须已存在一条记录，`id` 就是请求体里的 `alertMessageId`（worker 依赖它做状态流转和失败死信落库）
2. **`alert_rule`**：`alertRuleId` 对应记录必须存在且 `deleted=0`
3. **`alert_rule_template` + `message_template`**：规则关联的模板，`channel_type` 区分 `DINGDING` / `EMAIL`；至少备一个 `is_default=true` 的兜底模板
4. **`message_channel`**：钉钉通道 `config_json` 里要有 `webhookUrl`（可选 `msgType`）；邮件通道要有 `from`/`subject`/`format`

最小 SQL 示例见 [`worker-standalone-test.md` 第 5 节](./worker-standalone-test.md)。

---

## 4. 钉钉机器人数据库配置

### 4.1 在钉钉群创建机器人

1. 打开目标钉钉群 → **群设置 → 智能群助手 → 添加机器人 → 自定义机器人**。
2. 安全设置选择（三种可任意组合，勾选多个须**全部满足**）：
   - **自定义关键词**：消息内容须包含至少一个关键字（钉钉最多可配 10 个，含其中一个即可）；在 `config_json` 配 `keyword` 数组后，发送与测试消息会自动把关键字前置到内容
   - **加签**：复制机器人的 **Secret**（形如 `SECxxxxxxxx`），填入 `config_json.secret`，发送时自动按 `timestamp + "\n" + secret` 计算 HMAC-SHA256 签名
   - **IP 白名单**：填 worker 所在机器出口 IP
3. 创建完成后复制 **Webhook 地址**（形如 `https://oapi.dingtalk.com/robot/send?access_token=xxx`）。

### 4.2 写入 `message_channel` 表

表结构（`docs/design/alertworker-schema.sql`）：

| 列 | 说明 |
|---|---|
| `id` | 通道 id（即请求体 `recipients[].messageChannelId` 引用的值） |
| `name` | 通道名称（如 `ops-dingding`） |
| `channel_type` | 固定 `DINGDING` |
| `config_json` | JSON 配置，见下表 |
| `deleted` | `0` |

`config_json` 支持字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `webhookUrl` | **是** | 钉钉机器人 Webhook 地址 |
| `secret` | 否 | 机器人加签密钥（勾选「加签」时**必填**），发送时自动做 HMAC-SHA256 签名 |
| `msgType` | 否 | 消息类型：`text`（默认）/ `markdown` / `action_card`；其他值按 `text` 处理 |
| `keyword` | 否 | 自定义关键字，**字符串数组**（钉钉最多 10 个，消息含其中一个即通过）；发送与测试时自动**追加到消息末尾**：`markdown`/`action_card` 下渲染为**小标题**（`###### 关键字`），`text` 下为末尾独立一行 |
| `actionCard` | `action_card` 时 | 对象，可配 `title`（默认 `Alert`）、`singleTitle`（默认 `View`）、`singleUrl` |

#### 三种消息类型的 `config_json` 与 SQL 示例

**text（默认）**：

```sql
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (1, 'ops-dingding-text', 'DINGDING',
        '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx"}');
-- 等价于显式写 msgType:
-- '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","msgType":"text"}'
```

**markdown**（标题固定为 `Alert`，正文为模板渲染结果）：

```sql
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (2, 'ops-dingding-md', 'DINGDING',
        '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","msgType":"markdown"}');
```

**action_card**（带跳转按钮）：

```sql
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (3, 'ops-dingding-card', 'DINGDING',
        '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","msgType":"action_card","actionCard":{"title":"告警通知","singleTitle":"查看详情","singleUrl":"http://your-monitor.example.com"}}');
```

**加签 + 关键字**（机器人同时勾选「加签」和「自定义关键字」时）：

```sql
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (4, 'ops-dingding-signed', 'DINGDING',
        '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","secret":"SECxxxxxxxx","msgType":"markdown","keyword":["告警","报警"]}');
```

> 注意：`secret` 只需写在 `config_json` 里，worker 发送前会自动把 `timestamp` 和 `sign` 追加到 Webhook URL；配置 `keyword` 后，worker 发送和 `POST /api/v1/channels/{id}/test` 测试消息都会自动把关键字追加到消息末尾：`markdown`/`action_card` 渲染为小标题（`###### 告警 报警`），`text` 为末尾独立一行。

### 4.3 关联模板与接收人

通道配好后，还需两步打通：

1. **模板**：`alert_rule_template(rule_id, template_id, channel_type='DINGDING')` 关联到 `message_template`（`channel_type='DINGDING'`），或依赖 `is_default=true` 兜底模板。
2. **接收人**：HTTP 请求体里 `recipients[].channelType = "DINGDING"`、`recipients[].messageChannelId` 指向上面的通道 `id`。

> 验证通道是否可用：直接发一条 `POST /pw/alertworker/worker/api/v1/worker/dispatch`（见第 2 节），或走 DataManager 的 `POST /pw/alertworker/gw/api/v1/channels/{id}/test` 通道测试接口。
>
> 通过前端页面或 DataManager API 配置钉钉通道的完整步骤，另见 [`dingding-alert-config.md`](./dingding-alert-config.md)。

---

## 5. 处理行为说明

调用成功后，worker 依次执行：

```
状态置 DELIVERED
→ 按 alertRuleId 查 alert_rule / alert_rule_template / message_template / message_channel
→ 按每个接收人的 channelType 取模板（无则用 is_default=true 兜底模板）
→ Jinjava 渲染（context 并入 recipient）
→ MessageChannelDispatcher 分发（钉钉 webhook / 邮件 SMTP）
→ 至少一个成功或接收人为空 → 状态置 success
→ 全部失败 / 异常 → FAILED(worker_failed) → 按规则重试（retry_backoff 指数退避）
→ 重试耗尽 → 写 dead_letter_message
```

> 注意：HTTP 直调方式**不会自动重试**——重试逻辑在 `AlertMessageProcessor` 里只更新 DB 状态并返回 `retry` 标记，实际重推由 MQ 消费路径完成。因此 HTTP 调用失败后，需要调用方根据 `error` 自行排查或重新调用。

---

## 6. 调用示例

```bash
curl -X POST http://localhost:8082/pw/alertworker/worker/api/v1/worker/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "alertMessageId": 42,
    "alertRuleId": 1,
    "context": {
      "title": "CPU 使用率过高",
      "content": "node-01 CPU 使用率超过 90%"
    },
    "recipients": [
      {
        "assigneeId": 1,
        "cnName": "张三",
        "phone": "13800138000",
        "dingdingUsername": "zhangsan",
        "channelType": "DINGDING",
        "messageChannelId": 1
      }
    ]
  }'
```

---

## 7. 参考

- 接口实现：`alertworker-worker/.../controller/DebugDispatchController.java`
- 处理逻辑：`alertworker-worker/.../service/AlertMessageProcessor.java`
- 报文 DTO：`alertworker-common/.../dto/AlertMessagePayload.java`、`AlertRecipient.java`
- 通道分发：`alertworker-worker/.../channel/MessageChannelDispatcher.java`、`DingDingChannelHandler.java`、`EmailChannelHandler.java`
- 独立测试（含 DB 准备与排查）：[`worker-standalone-test.md`](./worker-standalone-test.md)
- 全系统接口总览：[`api-document.md`](./api-document.md)
