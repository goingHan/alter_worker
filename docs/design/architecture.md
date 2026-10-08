<!-- Generated: 2026-07-14 | Files scanned: 74 | Token estimate: ~700 -->

# AlertWorker Architecture

## System Diagram

```
                        ┌──────────────┐
                        │  Frontend     │
                        │  Vue 3 +     │
                        │  iView + GSAP │
                        └──────┬───────┘
                               │ HTTP (port 3000)
                               ▼
┌─────────────────────────────────────────────────────┐
│                 DataManager (port 8080)              │
│  CRUD: ApiKeys, Channels, Assignees, Groups,        │
│        Templates, Rules, Dictionaries               │
│  Read: Alert History, Delivery Logs, Failed Msgs    │
│  SSE:  /api/v1/sse/alerts (real-time stream)        │
└──────────┬────────────────────────────────┬─────────┘
           │ JDBC                            │ Nacos config
           ▼                                 ▼
┌──────────────────┐            ┌──────────────────────┐
│   PostgreSQL 15  │            │  Nacos 2.3           │
│   alertworker    │            │  Config Center       │
│   (Flyway migr.) │            │  (alertworker-shared)│
└──────────────────┘            └──────────────────────┘

┌─────────────────────────────────────────────────────┐
│              Gateway (port 8081)                     │
│  POST /alerts/prometheus     POST /alerts/json       │
│  POST /channels/{id}/test                            │
│                                                      │
│  Filter: ApiKeyAuthFilter → RateLimiterService       │
│  Parser: PrometheusAlertParser / JsonAlertParser     │
│  Matcher: AlertRuleMatcher (by label / key_name)     │
│  Enqueue: AlertMessageProducer → RabbitMQ            │
└──────────────────┬──────────────────────────────────┘
                   │ AMQP (port 5672)
                   ▼
┌─────────────────────────────────────────────────────┐
│  RabbitMQ 3.12                                      │
│  Exchange: alertworker.exchange                     │
│  Queue:    alertworker.queue                        │
│  DLX/DLQ:  alertworker.dlx / alertworker.dlq       │
└──────────────────┬──────────────────────────────────┘
                   │ AMQP
                   ▼
┌─────────────────────────────────────────────────────┐
│              Worker (port 8082)                      │
│  Consumer: AlertMessageConsumer                     │
│  Dispatcher: MessageChannelDispatcher               │
│    → DingDingChannelHandler (ActionCard/md/text)    │
│    → EmailChannelHandler    (HTML/plain)            │
│  Retry: exponential backoff (configurable)          │
│  Dead letter → dead_letter_message table            │
└─────────────────────────────────────────────────────┘
```

## Service Boundaries

| Service | Stateful? | Scales | Depends On |
|---|---|---|---|
| DataManager | Stateless | Horizontally | Postgres, Nacos |
| Gateway | Stateless | Horizontally | Postgres, Nacos, RabbitMQ |
| Worker | Stateless | Horizontally | Postgres, Nacos, RabbitMQ |
| Frontend | Stateless | CDN/cache | DataManager API |

## Data Flow (Alert Lifecycle)

```
1. External system → POST /pw/alertworker/gw/api/v1/alerts/*  (Gateway)
2. Gateway validates API key, rate limits, parses alert, matches rule
3. Gateway → RabbitMQ (AlertMessagePayload with context + recipients)
4. Worker ← RabbitMQ (consumes)
5. Worker re-queries rule, loads templates, renders via Jinjava
6. Worker → Channel (DingDing / Email) per recipient
7. Status transitions: recieved → GW_SENT → DELIVERED → WORKER_SENT → success
   Failures: → FAILED / gw_failed / worker_failed
   Retry exhausted → dead_letter_message
   Silenced rule → SILENCED (terminal, at step 2.5: no recipients, no MQ, dashboard-visible)
```

### Silence Path（2026-09-17 起）

```
Gateway ingest → rule matched → rule.silenced == true
  → persist alert_message (status = SILENCED, raw payload kept)
  → HTTP 200 {status: "SILENCED", silenced: true, recipientCount: 0}
  → NO recipient resolution, NO channel, NO RabbitMQ message
```

### Unmatched Path（2026-09-17 起）

```
Gateway ingest → no rule matched (matcher returned null)
  → persist alert_message (status = UNMATCHED, alert_rule_id = NULL, raw payload kept)
  → HTTP 200 {status: "UNMATCHED", unmatched: true, reason: "No matching alert rule"}
  → NO recipient resolution, NO channel, NO RabbitMQ message, NO retry, NO dead letter
```

两种丢弃都不算失败：`failed` 只统计 `FAILED`，丢弃分别计入 `silenced` / `unmatched`，并在仪表盘各有独立卡片与面板。

## Key Design Decisions

- **Nacos config center**: Shared config pulled at runtime, refreshable without restart
- **Direct Mapper injection**: Controllers use MyBatis-Plus Mappers directly (no Service layer abstraction in DataManager)
- **Single generic Worker**: One consumer handles all channel types, routing by `ChannelType` enum
- **Flyway migrations**: Run on every backend service startup (disable on all but one in production)
- **SSE polling**: DataManager polls DB every 3s to push new alerts to frontend
