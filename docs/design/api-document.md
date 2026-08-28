# AlertWorker API Documentation

## Overview

| Service | Base URL | Port |
|---------|----------|------|
| DataManager | `http://localhost:8080` | 8080 |
| MessageGateway | `http://localhost:8081` | 8081 |
| Worker | `http://localhost:8082` | 8082 |

---

## Gateway APIs

### 1. Submit Prometheus Alert

**Endpoint**: `POST /api/v1/alerts/prometheus`

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

**Error Response**:
```json
{
  "error": "No matching alert rule"
}
```

---

### 2. Submit JSON Alert

**Endpoint**: `POST /api/v1/alerts/json`

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

**Endpoint**: `POST /api/v1/channels/{id}/test`

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

## DataManager APIs

### API Keys

#### List API Keys
- **GET** `/api/v1/api-keys?current=1&size=20`
- **Response**: MyBatis-Plus `Page<ApiKey>`

#### Get API Key
- **GET** `/api/v1/api-keys/{id}`

#### Create API Key
- **POST** `/api/v1/api-keys`
- **Body**:
```json
{
  "name": "production",
  "apiValue": "prod-key-123",
  "rateLimit": 100
}
```

#### Update API Key
- **PUT** `/api/v1/api-keys/{id}`

#### Delete API Key
- **DELETE** `/api/v1/api-keys/{id}`

---

### Message Channels

#### List Channels
- **GET** `/api/v1/channels?current=1&size=20`

#### Get Channel
- **GET** `/api/v1/channels/{id}`

#### Create Channel
- **POST** `/api/v1/channels`
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
- **PUT** `/api/v1/channels/{id}`

#### Delete Channel
- **DELETE** `/api/v1/channels/{id}`

---

### Assignees

#### List Assignees
- **GET** `/api/v1/assignees?current=1&size=20`

#### Get Assignee
- **GET** `/api/v1/assignees/{id}`

#### Create Assignee
- **POST** `/api/v1/assignees`
- **Body**:
```json
{
  "cnName": "Zhang San",
  "phone": "13800138000",
  "dingdingUsername": "zhangsan"
}
```

#### Update Assignee
- **PUT** `/api/v1/assignees/{id}`

#### Delete Assignee
- **DELETE** `/api/v1/assignees/{id}`

---

### Communication Groups

#### List Groups
- **GET** `/api/v1/groups?current=1&size=20`

#### Get Group
- **GET** `/api/v1/groups/{id}`

#### Create Group
- **POST** `/api/v1/groups`
- **Body**:
```json
{
  "name": "ops-team",
  "sendType": "ALL"
}
```

#### Update Group
- **PUT** `/api/v1/groups/{id}`

#### Delete Group
- **DELETE** `/api/v1/groups/{id}`

#### List Group Assignees
- **GET** `/api/v1/groups/{id}/assignees`

#### Add Assignee to Group
- **POST** `/api/v1/groups/{id}/assignees`
- **Body**:
```json
{
  "assigneeId": 1,
  "channelType": "DINGDING",
  "serviceTypes": "demo,prod"
}
```

#### Remove Assignee from Group
- **DELETE** `/api/v1/groups/{id}/assignees/{assigneeId}`

---

### Message Templates

#### List Templates
- **GET** `/api/v1/templates?current=1&size=20`

#### Get Template
- **GET** `/api/v1/templates/{id}`

#### Create Template
- **POST** `/api/v1/templates`
- **Body**:
```json
{
  "name": "alert-dingding",
  "channelType": "DINGDING",
  "content": "Alert: {{ title }} - {{ content }}",
  "isDefault": false
}
```

#### Update Template
- **PUT** `/api/v1/templates/{id}`

#### Delete Template
- **DELETE** `/api/v1/templates/{id}`

#### Preview Template
- **POST** `/api/v1/templates/{id}/preview`
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
- **GET** `/api/v1/rules?current=1&size=20`

#### Get Rule
- **GET** `/api/v1/rules/{id}`

#### Create Rule
- **POST** `/api/v1/rules`
- **Body**:
```json
{
  "name": "cpu-alert",
  "alertType": "PROMETHEUS",
  "matchKey": "alertname",
  "matchValue": "CPUHigh",
  "retryMax": 3,
  "retryBackoff": "5,10,20"
}
```

#### Update Rule
- **PUT** `/api/v1/rules/{id}`

#### Delete Rule
- **DELETE** `/api/v1/rules/{id}`

#### List Rule Groups
- **GET** `/api/v1/rules/{id}/groups`

#### Add Group to Rule
- **POST** `/api/v1/rules/{id}/groups`
- **Body**:
```json
{
  "groupId": 1
}
```

#### Remove Group from Rule
- **DELETE** `/api/v1/rules/{id}/groups/{groupId}`

#### List Rule Templates
- **GET** `/api/v1/rules/{id}/templates`

#### Add Template to Rule
- **POST** `/api/v1/rules/{id}/templates`
- **Body**:
```json
{
  "templateId": 1,
  "channelType": "DINGDING"
}
```

#### Remove Template from Rule
- **DELETE** `/api/v1/rules/{id}/templates/{templateId}`

---

### Alert History & Monitoring

#### Alert History
- **GET** `/api/v1/alerts/history?current=1&size=20`
- **Response**: `Page<AlertMessage>`

#### Alert Detail
- **GET** `/api/v1/alerts/history/{id}`

#### Delivery Logs
- **GET** `/api/v1/alerts/delivery-logs?status=success&current=1&size=20`
- Query `status` values: `recieved`, `GW_SENT`, `DELIVERED`, `WORKER_SENT`, `FAILED`, `success`

#### Failed Messages
- **GET** `/api/v1/alerts/failed?current=1&size=20`

#### Dead Letters
- **GET** `/api/v1/alerts/dead-letters?current=1&size=20`

---

### Real-Time Alerts (SSE)

#### SSE Stream
- **GET** `/api/v1/sse/alerts`
- **Content-Type**: `text/event-stream`
- **Event Name**: `alerts`
- **Data**: JSON array of new `AlertMessage` records emitted every 3 seconds

**JavaScript Example**:
```javascript
const eventSource = new EventSource('/api/v1/sse/alerts');
eventSource.addEventListener('alerts', (e) => {
  const newAlerts = JSON.parse(e.data);
  console.log(newAlerts);
});
```

---

### Dictionary

#### List Dictionaries
- **GET** `/api/v1/dictionaries?current=1&size=20&dictType=message_status`

#### Get by Type
- **GET** `/api/v1/dictionaries/types/{dictType}`

#### Create Dictionary Entry
- **POST** `/api/v1/dictionaries`
- **Body**:
```json
{
  "dictType": "message_status",
  "dictKey": "GW_SENT",
  "dictValue": "Gateway Sent"
}
```

#### Update Dictionary Entry
- **PUT** `/api/v1/dictionaries/{id}`

#### Delete Dictionary Entry
- **DELETE** `/api/v1/dictionaries/{id}`

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
  "apiValue": "prod-key-123",
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
  "createdAt": "2026-04-16T10:00:00",
  "updatedAt": "2026-04-16T10:00:00",
  "deleted": 0
}
```

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

### MessageSubStatus
- `gw_failed`
- `worker_failed`

### DingDingMsgType
- `ActionCard`
- `markdown`
- `text`
