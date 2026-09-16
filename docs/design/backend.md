<!-- Generated: 2026-07-14 | Files scanned: 38 | Token estimate: ~950 -->

# Backend Architecture

## Entry Points

| Class | Path | Port |
|---|---|---|
| `DataManagerApplication` | `alertworker-datamanager/` | 8083 (prefix `/pw/alertworker/dm`) |
| `GatewayApplication` | `alertworker-gateway/` | 8081 (prefix `/pw/alertworker/gw`) |
| `WorkerApplication` | `alertworker-worker/` | 8082 (prefix `/pw/alertworker/worker`) |

## DataManager — CRUD Routes (port 8083, context-path `/pw/alertworker/dm`; full path = that prefix + path below)

All routes are `GET/POST/PUT/DELETE` standard CRUD unless noted.

### /api/v1/api-keys → `ApiKeyController`
- Full CRUD on API keys (name, value, rate_limit)

### /api/v1/channels → `MessageChannelController`
- Full CRUD on message channels (name, type, config_json)

### /api/v1/assignees → `AssigneeController`
- Full CRUD on assignees (phone, dingding_username, cn_name)

### /api/v1/groups → `CommunicationGroupController`
- Full CRUD + nested assignee management
- `GET /{id}/assignees` — list assignees in group
- `POST /{id}/assignees` — add assignee to group
- `DELETE /{id}/assignees/{assigneeId}` — remove assignee

### /api/v1/templates → `MessageTemplateController`
- Full CRUD + `POST /{id}/preview` — test template rendering

### /api/v1/rules → `AlertRuleController`
- Full CRUD + nested group + template management
- `GET/POST/DELETE /{id}/groups` — manage linked groups
- `GET/POST/DELETE /{id}/templates` — manage linked templates

### /api/v1/dictionaries → `DictionaryController`
- CRUD + `GET /types/{dictType}` — filter by dict_type

### /api/v1/alerts → `AlertMessageController`
- `GET /history` — paginated alert history
- `GET /history/{id}` — single alert detail
- `GET /delivery-logs` — delivery trace
- `GET /failed` — failed messages dashboard
- `GET /dead-letters` — dead letter messages

### /api/v1/sse/alerts → `AlertSseController`
- `GET /sse/alerts` — real-time SSE stream (polls DB every 3s)

## Gateway — Alert Ingestion Routes (port 8081, context-path `/pw/alertworker/gw`)

### /gw/api/v1 → `AlertController`
- `POST /alerts/prometheus` — Prometheus webhook format
- `POST /alerts/json` — custom JSON format
- `POST /channels/{id}/test` — channel connectivity test

### Middleware Chain
```
Request → ApiKeyAuthFilter (Javax Filter, Order 1)
            ├── skip if not /api/v1/alerts* (context-relative path within /gw)
            ├── validate X-API-Key header → ApiKeyMapper
            ├── rate limit check → RateLimiterService (token bucket)
            └── set request attribute "apiKeyId"

Request → AlertController.handleAlert()
            ├── parse → PrometheusAlertParser / JsonAlertParser
            ├── match → AlertRuleMatcher (by label/key_name + service_type)
            ├── build → MessageBuilder (resolve recipients + context)
            ├── enqueue → AlertMessageProducer → RabbitMQ
            └── transition → GatewayAlertMessageService (DB status)
```

## Worker — Consumer (port 8082)

### Consumer Chain
```
RabbitMQ → AlertMessageConsumer.onMessage()
             ├── transition → DELIVERED
             ├── re-query → AlertRuleMapper (rule + templates)
             ├── render → JinjavaTemplateService (per recipient channel_type)
             ├── dispatch → MessageChannelDispatcher
             │               ├── DingDingChannelHandler (ActionCard/markdown/text)
             │               └── EmailChannelHandler (HTML/plain)
             └── retry → exponential backoff → dead_letter if exhausted
```

## Shared Library (`alertworker-common`)

### Entities (15 tables → 15 entities)
`BaseEntity` (id, createdAt, updatedAt, deleted) → extended by all entities

### Mappers (14 MyBatis-Plus Mappers)
Direct `BaseMapper<T>` interfaces — no custom SQL in current codebase

### Enums
`AlertType` (PROMETHEUS / JSON), `ChannelType` (DINGDING / EMAIL),
`DingDingMsgType` (ActionCard / markdown / text),
`MessageStatus`, `MessageSubStatus`, `SendType`

### Services
- `AuditLogService` — logs all CRUD actions
- `JinjavaTemplateService` — Jinjava template rendering engine

### Config
- `MyBatisPlusConfig` — pagination plugin, optimistic lock
- `MyMetaObjectHandler` — auto-fill createdAt/updatedAt
- `RabbitMQConfig` — exchange, queue, DLX/DLQ bindings
