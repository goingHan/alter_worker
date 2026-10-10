# AlertWorker API Documentation

## Overview

每个后端模块通过 `server.servlet.context-path` 自带 URL 前缀，从 URL 即可区分应用：

| Service | Base URL | Port | Prefix |
|---------|----------|------|--------|
| DataManager | `http://localhost:8083/pw/alertworker/dm` | 8083 | `/pw/alertworker/dm` |
| MessageGateway | `http://localhost:8081/pw/alertworker/gw` | 8081 | `/pw/alertworker/gw` |
| Worker | `http://localhost:8082/pw/alertworker/worker` | 8082 | `/pw/alertworker/worker` |

下文端点路径均以完整路径（工程前缀 + 模块前缀）书写。

---

## Gateway APIs

### 1. Submit Prometheus Alert

**Endpoint**: `POST /gw/pw/alertworker/dm/api/v1/alerts/prometheus`

**Headers**:
- `X-API-Key` (required): Valid API key value
- `Content-Type`: `application/json`

**Required Payload Structure**:

The payload **must** follow the standard Prometheus Alertmanager webhook format with the following requirements:

| Field | Required | Description |
|-------|----------|-------------|
| `alerts` | **Yes** | Array of alert objects. At least one alert is required. |
| `alerts[0].labels` | **Yes** | Object containing label key-value pairs. |
| `alerts[0].labels.{matchKey}` | **Yes** | The label key must match the `matchKey` configured in the target `AlertRule`. |
| `alerts[0].labels.service_type` | Conditional | Required if the matched `CommunicationGroup` uses `sendType = SERVICE_TYPE` for recipient filtering. |
| `alerts[0].annotations` | Recommended | Used for human-readable alert content in templates. |

**Rule Matching Logic**:
- Gateway looks up `AlertRule` where `alertType = PROMETHEUS`.
- It extracts the value of the label defined by the rule's `matchKey` from `alerts[0].labels`.
- If the rule defines a `matchValue`, the extracted label value must equal it.
- `service_type` is also extracted from `alerts[0].labels.service_type` for assignee filtering.

**Request Body Example**:
```json
{
  "receiver": "alertworker",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "CPUHigh",
        "service_type": "order-service",
        "severity": "critical",
        "instance": "server-01"
      },
      "annotations": {
        "summary": "High CPU usage detected",
        "description": "CPU usage is above 85% for 5 minutes"
      },
      "startsAt": "2026-04-16T10:00:00Z"
    }
  ],
  "externalURL": "http://prometheus:9093"
}
```

**Matching Example**:
- `AlertRule.matchKey = "alertname"`
- `AlertRule.matchValue = "CPUHigh"`
- The rule matches because `alerts[0].labels.alertname == "CPUHigh"`.
- Assignees are filtered by `service_type = "order-service"` if groups use `SERVICE_TYPE`.

**Response**:
```json
{
  "messageId": 1,
  "status": "GW_SENT",
  "recipientCount": 2
}
```

**Dropped Response（2026-09-17 起，200）**:
```json
{
  "messageId": 7,
  "recordId": 42,
  "status": "UNMATCHED",
  "silenced": false,
  "unmatched": true,
  "recipientCount": 0,
  "reason": "No matching alert rule"
}
```
- `status = "UNMATCHED"`：没有任何规则命中 → 已直接丢弃（不重试、不进死信、不发渠道），仅落一条 `UNMATCHED` 记录供仪表盘统计
- `status = "SILENCED"`（`silenced: true`）：命中的规则处于静默状态 → 同样直接丢弃
- 两种情况都返回 **200**（告警已受理处理），避免上游按 `repeat_interval` 无限重发；真正失败（如持久化异常）才返回 5xx

**Error Response**:
```json
{
  "error": "Failed to persist alert message"
}
```

---

### 2. Submit JSON Alert

**Endpoint**: `POST /gw/pw/alertworker/dm/api/v1/alerts/json`

**Headers**:
- `X-API-Key` (required): Valid API key value
- `Content-Type`: `application/json`

**Required Payload Structure**:

The payload **must** be a valid JSON object with the following requirements:

| Field | Required | Description |
|-------|----------|-------------|
| `{matchKey}` | **Yes** | The top-level key defined by the target `AlertRule.matchKey`. Its value is used for rule matching. |
| `service_type` | Conditional | Required if the matched `CommunicationGroup` uses `sendType = SERVICE_TYPE` for recipient filtering. |
| Any template variables | Recommended | Keys used in the linked `MessageTemplate` Jinjava content (e.g., `title`, `content`, `level`). |

**Rule Matching Logic**:
- Gateway looks up `AlertRule` where `alertType = JSON`.
- It reads the top-level value of the key defined by the rule's `matchKey`.
- If the rule defines a `matchValue`, the extracted value must equal it.
- `service_type` is extracted from the top-level `service_type` key for assignee filtering.

**Request Body Example**:
```json
{
  "alertname": "MemoryHigh",
  "service_type": "payment-service",
  "level": "warning",
  "title": "Memory Usage High",
  "content": "Memory usage on payment-service-01 is at 92%",
  "timestamp": "2026-04-16T10:00:00Z"
}
```

**Matching Example**:
- `AlertRule.matchKey = "alertname"`
- `AlertRule.matchValue = "MemoryHigh"`
- The rule matches because the JSON payload contains `"alertname": "MemoryHigh"`.
- Assignees are filtered by `service_type = "payment-service"` if groups use `SERVICE_TYPE`.

**Response**:
```json
{
  "messageId": 2,
  "status": "GW_SENT",
  "recipientCount": 1
}
```

---

### 3. Test Message Channel

**Endpoint**: `POST /gw/pw/alertworker/dm/api/v1/channels/{id}/test`

**Response (Success)**:
```json
{
  "success": true,
  "message": "Channel test passed"
}
```

**Response (Failure)**:
```json
{
  "success": false,
  "message": "Channel test failed"
}
```

---

### 4. Test Alert Through Rules（2026-09-16 起）

**Endpoint**: `POST /pw/alertworker/gw/api/v1/test-alerts`

在规则页勾选多条规则后发送测试告警。测试消息走与生产一致的全链路（写 `alert_message` → RabbitMQ → worker 渲染分发），最终状态可在「告警历史」查看。

**Body**:

| 字段 | 必填 | 说明 |
|---|---|---|
| `ruleIds` | 随机模式必填 | 选中的规则 id 列表 |
| `channelId` | 否 | 指定则强制走该 `message_channel`；留空按规则绑定通道（未绑定走默认通道） |
| `mode` | 否 | `auto`（默认，随机生成、跳过 matcher）/ `payload`（手动报文、走真实 matcher） |
| `alertType` | 否 | `payload` 模式下的报文类型，默认取第一条规则的类型 |
| `rawPayload` | 否 | 手动上报的报文原文；有值时即视为 `payload` 模式 |

**Response**:
```json
{
  "mode": "auto",
  "message": "已提交 2 条测试告警",
  "silencedCount": 0,
  "unmatchedCount": 0,
  "results": [
    {
      "ruleId": 1,
      "ruleName": "cpu-alert",
      "matchedRuleId": 1,
      "matchedRuleName": "cpu-alert",
      "messageNo": "1758000000000001",
      "recordId": 42,
      "recipientCount": 2,
      "success": true,
      "silenced": false,
      "unmatched": false,
      "error": null
    }
  ]
}
```

- `auto` 模式：为每条选中规则生成随机报文（Prometheus 生成 Alertmanager webhook 结构，JSON 生成平铺键值，规则条件里的字段会填入示例值），跳过 matcher 直接使用该规则
- `payload` 模式：报文交给真实 `AlertRuleMatcher` 匹配，`matchedRule*` 为实际命中的规则
- `apiKeyId` 自动取第一条未删除的 API 密钥（无密钥时返回 400）
- `silenced` / `unmatched`（2026-09-17 起）：该条测试告警是否被丢弃 —— 前者是规则处于静默，后者是报文（`payload` 模式）没命中任何规则；`message` 与 `silencedCount` / `unmatchedCount` 会一并说明丢弃数量，前端结果列分别显示「已静默丢弃」「未匹配丢弃」而不是「已提交」

---

## DataManager APIs

### API Keys

#### List API Keys
- **GET** `/pw/alertworker/dm/api/v1/api-keys?current=1&size=20`
- **Response**: MyBatis-Plus `Page<ApiKey>`

#### Get API Key
- **GET** `/pw/alertworker/dm/api/v1/api-keys/{id}`

#### Reveal API Key
- **GET** `/pw/alertworker/dm/api/v1/api-keys/{id}/reveal`
- **Response**: 完整 `ApiKey` 对象，`apiValue` 为**明文**密钥（不脱敏）。仅供管理台编辑弹窗回显原始密钥使用，操作会记录 `REVEAL` 审计日志。

#### Create API Key
- **POST** `/pw/alertworker/dm/api/v1/api-keys`
- **Body**（`apiValue` 由服务端自动生成，无需传入，传入值会被忽略）:
```json
{
  "name": "production",
  "rateLimit": 100
}
```
- **Response**: 完整 `ApiKey` 对象，`apiValue` 为生成的明文密钥（格式 `aw-` + 64 位十六进制），**仅创建时返回一次**；列表/详情接口均返回掩码值。

#### Update API Key
- **PUT** `/pw/alertworker/dm/api/v1/api-keys/{id}`

#### Delete API Key
- **DELETE** `/pw/alertworker/dm/api/v1/api-keys/{id}`

---

### Message Channels

#### List Channels
- **GET** `/pw/alertworker/dm/api/v1/channels?current=1&size=20`

#### Get Channel
- **GET** `/pw/alertworker/dm/api/v1/channels/{id}`

#### Create Channel
- **POST** `/pw/alertworker/dm/api/v1/channels`
- **Body** (DingDing):
```json
{
  "name": "ops-dingding",
  "channelType": "DINGDING",
  "configJson": "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=xxx\",\"secret\":\"SECxxx\",\"msgType\":\"text\",\"keyword\":[\"告警\",\"报警\"]}"
}
```
`configJson` 可选字段：`secret`（加签密钥，机器人勾选「加签」时必填）、`msgType`（`text`/`markdown`/`action_card`）、`keyword`（自定义关键字数组，发送时自动前置到消息内容）、`actionCard`（`action_card` 消息类型的按钮参数）。
- **Body** (Email):
```json
{
  "name": "ops-email",
  "channelType": "EMAIL",
  "configJson": "{\"from\":\"alerts@example.com\",\"subject\":\"Alert Notification\",\"format\":\"html\"}"
}
```

#### Update Channel
- **PUT** `/pw/alertworker/dm/api/v1/channels/{id}`
- Body 中的 `isDefault: true` 会把该通道设为所属渠道类型的默认通道（同类型仅保留一个默认）

#### Set Default Channel（2026-09-16 起）
- **PUT** `/pw/alertworker/dm/api/v1/channels/{id}/default`
- **Body**: `{"default": true}`（`false` 取消默认）
- 未显式绑定通道的规则会走对应渠道类型的默认通道

#### Delete Channel
- **DELETE** `/pw/alertworker/dm/api/v1/channels/{id}`

---

### Assignees

#### List Assignees
- **GET** `/pw/alertworker/dm/api/v1/assignees?current=1&size=20`

#### Get Assignee
- **GET** `/pw/alertworker/dm/api/v1/assignees/{id}`

#### Create Assignee
- **POST** `/pw/alertworker/dm/api/v1/assignees`
- **Body**:
```json
{
  "cnName": "Zhang San",
  "phone": "13800138000",
  "dingdingUsername": "zhangsan"
}
```

#### Update Assignee
- **PUT** `/pw/alertworker/dm/api/v1/assignees/{id}`

#### Delete Assignee
- **DELETE** `/pw/alertworker/dm/api/v1/assignees/{id}`

---

### Communication Groups

#### List Groups
- **GET** `/pw/alertworker/dm/api/v1/groups?current=1&size=20`

#### Get Group
- **GET** `/pw/alertworker/dm/api/v1/groups/{id}`

#### Create Group
- **POST** `/pw/alertworker/dm/api/v1/groups`
- **Body**:
```json
{
  "name": "ops-team",
  "sendType": "ALL"
}
```

#### Update Group
- **PUT** `/pw/alertworker/dm/api/v1/groups/{id}`

#### Delete Group
- **DELETE** `/pw/alertworker/dm/api/v1/groups/{id}`

#### List Group Assignees
- **GET** `/pw/alertworker/dm/api/v1/groups/{id}/assignees`

#### Add Assignee to Group
- **POST** `/pw/alertworker/dm/api/v1/groups/{id}/assignees`
- **Body**:
```json
{
  "assigneeId": 1,
  "channelType": "DINGDING",
  "serviceTypes": "demo,prod"
}
```

#### Remove Assignee from Group
- **DELETE** `/pw/alertworker/dm/api/v1/groups/{id}/assignees/{assigneeId}`

---

### Message Templates

#### List Templates
- **GET** `/pw/alertworker/dm/api/v1/templates?current=1&size=20`

#### Get Template
- **GET** `/pw/alertworker/dm/api/v1/templates/{id}`

#### Create Template
- **POST** `/pw/alertworker/dm/api/v1/templates`
- **Body**（模板不绑定渠道，`channelType` 由规则绑定 `alert_rule_template` 时指定，可不传）:
```json
{
  "name": "alert-dingding",
  "content": "Alert: {{ title }} - {{ content }}",
  "isDefault": false
}
```

#### Update Template
- **PUT** `/pw/alertworker/dm/api/v1/templates/{id}`

#### Delete Template
- **DELETE** `/pw/alertworker/dm/api/v1/templates/{id}`

#### Preview Template
- **POST** `/pw/alertworker/dm/api/v1/templates/{id}/preview`
- **Body**: Jinja2 context map
```json
{
  "title": "CPU High",
  "content": "Usage 95%"
}
```
- **Response**: Rendered string

---

### Alert Rules

#### List Rules
- **GET** `/pw/alertworker/dm/api/v1/rules?current=1&size=20`
- 过滤参数：`name`（模糊）、`alertType`（`PROMETHEUS`/`JSON`）、`silenced`（`true`/`false`，2026-09-17 起）

#### Get Rule
- **GET** `/pw/alertworker/dm/api/v1/rules/{id}`

#### Create Rule
- **POST** `/pw/alertworker/dm/api/v1/rules`
- **Body**:
```json
{
  "name": "cpu-alert",
  "alertType": "PROMETHEUS",
  "severity": "CRITICAL",
  "matchKey": "alertname",
  "matchValue": "CPUHigh",
  "matchLogic": "AND",
  "matchConditions": "[{\"key\":\"alertname\",\"value\":\"CPUHigh\",\"op\":\"AND\"},{\"key\":\"severity\",\"value\":\"critical\",\"op\":\"AND\"},{\"key\":\"env\",\"value\":\"prod\",\"op\":\"NOT\"}]",
  "channelIds": [3, 5],
  "retryMax": 3,
  "retryBackoff": "5,10,20",
  "silenced": false
}
```

- `severity`（2026-10-10 起）：规则自带的告警级别 `INFO`/`WARNING`/`CRITICAL`（默认 `WARNING`）。规则级别为权威值，渲染模板与日报统计时**覆盖**告警系统传入的级别（模板可用 `{{ severity }}` / `{{ labels.severity }}` / `{{ ruleSeverity }}`）
- `channelIds`（2026-09-16 起，Create/Update 均支持）：规则绑定的消息渠道 id 数组，可空；**全量替换**语义 —— 不传（null）= 不改动绑定，空数组 = 解除全部绑定（走渠道类型默认通道）
- `silenced`（2026-09-17 起）：规则静默开关，默认 `false`。为 `true` 时该规则匹配到的告警在网关直接丢弃（不解析接收人、不发送任何渠道），只落一条 `SILENCED` 状态的 `alert_message` 供仪表盘展示

**匹配条件逻辑**（2026-09-16 起）：
- `matchConditions`：JSON 数组，每项 `{key, value, op?}`；`op` ∈ `AND`（且）/ `OR`（或）/ `NOT`（非，等价于「且非」）
- 求值顺序：第一个条件为表达式起点（`AND`/`OR` 视为起点，`NOT` 表示对首个条件取反），其后各条件按其 `op` 与左侧结果结合，自上而下左结合。例：`A AND B NOT C` = `(A AND B) AND NOT C`
- 兼容：旧格式 `{key, value, not?}`（无 `op` 字段）仍按全局 `matchLogic`（`OR` 默认 / `AND`）+ `not` 取反求值；`matchLogic` 仅作为旧数据回退逻辑
- `value` 留空表示"字段存在即命中"；取反条件在字段不存在时也视为命中；`matchKey`/`matchValue` 始终保存第一个条件（兼容旧逻辑，实际匹配以 `matchConditions` 为准）

#### Update Rule
- **PUT** `/pw/alertworker/dm/api/v1/rules/{id}`
- 未传 `silenced` 时沿用数据库当前值；**开启/关闭静默建议用下面的专用接口**，避免普通编辑误翻转开关

#### Silence / Resume Rule（静默开关，2026-09-17 起）
- **PUT** `/pw/alertworker/dm/api/v1/rules/{id}/silence`
- **Body**: `{"silenced": true, "reason": "变更窗口临时降噪"}`（`reason` 仅入审计日志）
- **Response**: 更新后的规则对象（含 `silenced`）
- 语义：`silenced = true` 时，该规则匹配到的**所有**告警 —— 含规则页的「测试告警」—— 在网关摄取阶段被丢弃：
  - 不解析接收人、不解析渠道、不写入 RabbitMQ
  - 落一条 `alert_message`，`status = SILENCED`、`finished_at` = 丢弃时刻，报文原文照存
  - 提交方仍收到 `200`（告警已被受理处理），响应体 `status` = `SILENCED`、`silenced` = `true`
  - 审计日志记录 `SILENCE_ON` / `SILENCE_OFF`

#### Delete Rule
- **DELETE** `/pw/alertworker/dm/api/v1/rules/{id}`

#### List Rule Channels（2026-09-16 起）
- **GET** `/pw/alertworker/dm/api/v1/rules/{id}/channels`
- **Response**: `[{"ruleId":1,"channelId":3,"channelName":"ops-dingding","channelType":"DINGDING"}]`
- 规则可绑定多个通道（同类型多条 = 全部发送）；未绑定的规则走该渠道类型的默认通道

#### List All Rule Channels（规则表格用）
- **GET** `/pw/alertworker/dm/api/v1/rules/channels`

#### Bind Channel to Rule
- **POST** `/pw/alertworker/dm/api/v1/rules/{id}/channels`
- **Body**: `{"channelId": 3}`

#### Unbind Channel
- **DELETE** `/pw/alertworker/dm/api/v1/rules/{id}/channels/{channelId}`

#### Batch Replace Rule Channels（批量关联渠道）
- **PUT** `/pw/alertworker/dm/api/v1/rules/channels`
- **Body**: `{"ruleIds": [1, 2], "channelIds": [3, 5]}`
- 将选中规则的渠道绑定统一替换为 `channelIds`；`channelIds` 为空数组 = 解除全部绑定
- **Response**: `{"updated": 2}`

#### List Rule Groups
- **GET** `/pw/alertworker/dm/api/v1/rules/{id}/groups`

#### Add Group to Rule
- **POST** `/pw/alertworker/dm/api/v1/rules/{id}/groups`
- **Body**:
```json
{
  "groupId": 1
}
```

#### Remove Group from Rule
- **DELETE** `/pw/alertworker/dm/api/v1/rules/{id}/groups/{groupId}`

#### List Rule Templates
- **GET** `/pw/alertworker/dm/api/v1/rules/{id}/templates`

#### Add Template to Rule
- **POST** `/pw/alertworker/dm/api/v1/rules/{id}/templates`
- **Body**:
```json
{
  "templateId": 1,
  "channelType": "DINGDING"
}
```

#### Remove Template from Rule
- **DELETE** `/pw/alertworker/dm/api/v1/rules/{id}/templates/{templateId}`

---

### Alert History & Monitoring

#### Alert History
- **GET** `/pw/alertworker/dm/api/v1/alerts/history?current=1&size=20`
- **Response**: `Page<AlertMessage>`

#### Alert Detail
- **GET** `/pw/alertworker/dm/api/v1/alerts/history/{id}`

#### Delivery Logs
- **GET** `/pw/alertworker/dm/api/v1/alerts/delivery-logs?status=success&current=1&size=20`
- Query `status` values: `recieved`, `GW_SENT`, `DELIVERED`, `WORKER_SENT`, `FAILED`, `success`

#### Failed Messages
- **GET** `/pw/alertworker/dm/api/v1/alerts/failed?current=1&size=20`

#### Dead Letters
- **GET** `/pw/alertworker/dm/api/v1/alerts/dead-letters?current=1&size=20`

---

### Real-Time Alerts (SSE)

#### SSE Stream
- **GET** `/pw/alertworker/dm/api/v1/sse/alerts`
- **Content-Type**: `text/event-stream`
- **Event Name**: `alerts`
- **Data**: JSON array of new `AlertMessage` records emitted every 3 seconds

**JavaScript Example**:
```javascript
const eventSource = new EventSource('/pw/alertworker/dm/api/v1/sse/alerts');
eventSource.addEventListener('alerts', (e) => {
  const newAlerts = JSON.parse(e.data);
  console.log(newAlerts);
});
```

---

### Dictionary

#### List Dictionaries
- **GET** `/pw/alertworker/dm/api/v1/dictionaries?current=1&size=20&dictType=message_status`

#### Get by Type
- **GET** `/pw/alertworker/dm/api/v1/dictionaries/types/{dictType}`

#### Create Dictionary Entry
- **POST** `/pw/alertworker/dm/api/v1/dictionaries`
- **Body**:
```json
{
  "dictType": "message_status",
  "dictKey": "GW_SENT",
  "dictValue": "Gateway Sent"
}
```

#### Update Dictionary Entry
- **PUT** `/pw/alertworker/dm/api/v1/dictionaries/{id}`

#### Delete Dictionary Entry
- **DELETE** `/pw/alertworker/dm/api/v1/dictionaries/{id}`

---

## Payload Requirements Summary

### Prometheus Alerts

| Requirement | Location | Purpose |
|-------------|----------|---------|
| `alerts` array | Root | Must exist and contain at least one alert object. |
| `alerts[0].labels` | Inside first alert | Must exist as an object. |
| `alerts[0].labels.{matchKey}` | Inside `labels` | Used to match the `AlertRule` (e.g., `alertname`). |
| `alerts[0].labels.service_type` | Inside `labels` | Required for `SERVICE_TYPE` group filtering. |
| `alerts[0].annotations` | Inside first alert | Recommended for template rendering (e.g., `summary`, `description`). |

### JSON Alerts

| Requirement | Location | Purpose |
|-------------|----------|---------|
| `{matchKey}` | Top-level JSON key | Used to match the `AlertRule` (e.g., `alertname`, `event_type`). |
| `service_type` | Top-level JSON key | Required for `SERVICE_TYPE` group filtering. |
| Template variables | Top-level or nested | Any keys referenced by the linked `MessageTemplate` Jinjava content. |

---

## Data Models

### AlertMessage
```json
{
  "id": 1,
  "apiKeyId": 1,
  "alertRuleId": 2,
  "payload": "{...}",
  "status": "GW_SENT",
  "subStatus": null,
  "retryCount": 0,
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00",
  "deleted": 0
}
```

### ApiKey
```json
{
  "id": 1,
  "name": "production",
  "apiValue": "aw-3f9c...（仅创建响应返回明文，其余接口返回 **** 掩码）",
  "rateLimit": 100,
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00",
  "deleted": 0
}
```

### MessageChannel
```json
{
  "id": 1,
  "name": "ops-dingding",
  "channelType": "DINGDING",
  "configJson": "{\"webhookUrl\":\"...\"}",
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00",
  "deleted": 0
}
```

### AlertRule
```json
{
  "id": 1,
  "name": "cpu-alert",
  "alertType": "PROMETHEUS",
  "matchKey": "alertname",
  "matchValue": "CPUHigh",
  "retryMax": 3,
  "retryBackoff": "5,10,20",
  "silenced": false,
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00",
  "deleted": 0
}
```

---

## Dashboard Stats

#### 24h Overview
- **GET** `/pw/alertworker/dm/api/v1/stats/overview`
- **Response（2026-09-17 起新增后三项）**:
```json
{
  "total": 1280,
  "success": 1201,
  "failed": 12,
  "deadLetters": 3,
  "silenced": 64,
  "unmatched": 8,
  "silencedRules": 2
}
```
- `silenced`：最近 24 小时被静默规则丢弃的告警数（`status = SILENCED`，已包含在 `total` 内、不计入 `success`）
- `unmatched`：最近 24 小时没有命中任何规则、已直接丢弃的告警数（`status = UNMATCHED`，同样计入 `total`、不计入 `success`/`failed`）
- `silencedRules`：当前处于静默状态的规则数（全量，不限时间窗）

#### 24h Hourly Trend
- **GET** `/pw/alertworker/dm/api/v1/stats/24h`
- 每个桶：`{hour, total, failed, deadLetters, silenced, unmatched}`（后两项为 2026-09-17 起新增）

#### Recent Silenced Messages
- **GET** `/pw/alertworker/dm/api/v1/stats/silenced?size=10`（`size` 上限 100）
- **Response**: `[{id, messageNo, alertRuleId, ruleName, status, summary, fullContent, receivedAt, createdAt}]`
- 按 `created_at` 倒序；`summary` 为报文摘要（≤200 字符），完整报文在 `fullContent`

#### Recent Unmatched Messages（未匹配告警，2026-09-17 起）
- **GET** `/pw/alertworker/dm/api/v1/stats/unmatched?size=10`（`size` 上限 100）
- **Response**: 同上结构，但 `alertRuleId` / `ruleName` 恒为 `null`（未匹配就是没有规则）
- 用途：仪表盘「未匹配告警」面板 —— 出现即说明有告警没人接，通常是缺规则或匹配 key/值写错

#### Timing Stats
- **GET** `/pw/alertworker/dm/api/v1/stats/timing`

---

## Enums Reference

### AlertType
- `PROMETHEUS`
- `JSON`

### ChannelType
- `DINGDING`
- `EMAIL`

### SendType
- `SERVICE_TYPE`
- `ALL`

### MessageStatus
- `recieved`
- `GW_SENT`
- `DELIVERED`
- `WORKER_SENT`
- `FAILED`
- `success`
- `SILENCED`（2026-09-17 起：命中静默规则后在网关被丢弃的终态，不会出现在 MQ / Worker 侧）
- `UNMATCHED`（2026-09-17 起：没有命中任何规则、在网关直接丢弃的终态，不重试、不进死信）

### MessageSubStatus
- `gw_failed`
- `worker_failed`

### DingDingMsgType
- `ActionCard`
- `markdown`
- `text`
