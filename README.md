# AlertWorker

A transfer-station alert notification platform that accepts alert messages (Prometheus / JSON) and dispatches them to notification channels such as DingDing and Email.

## Tech Stack

- **Frontend**: Vue 3 + Vite + iView (View UI Plus) + GSAP
- **Backend**: Java 17 + Spring Boot 3 + MyBatis-Plus + Jinjava
- **Database**: PostgreSQL 15
- **Message Queue**: RabbitMQ 3.12
- **Config Center**: Nacos 2.3

## Project Structure

```
alertworker-parent/
├── alertworker-common/      # Shared entities, enums, mappers, services
├── alertworker-datamanager/ # Stateless configuration/CRUD API (port 8080)
├── alertworker-gateway/     # Alert ingestion & enqueueing (port 8081)
├── alertworker-worker/      # RabbitMQ consumer & dispatcher (port 8082)
├── frontend/                # Vue 3 management console (port 3000)
└── docker-compose.yml       # PostgreSQL + RabbitMQ + Nacos
```

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `localhost:5432` (and a MySQL 8 alternative on `localhost:3306` — point `spring.datasource.url` at `jdbc:mysql://...` to use it)
- RabbitMQ on `localhost:5672` (Management UI on `localhost:15672`)
- Nacos on `localhost:8848`

### 2. Import Nacos Shared Config

Upload `nacos-shared-config.yaml` to Nacos as `alertworker-shared.yaml` in the `DEFAULT_GROUP`.

### 3. Build Backend

```bash
mvn clean install
```

### 4. Start Services

Start each module in a separate terminal (or use IDE):

```bash
# DataManager (port 8080)
cd alertworker-datamanager
mvn spring-boot:run

# Gateway (port 8081)
cd alertworker-gateway
mvn spring-boot:run

# Worker (port 8082)
cd alertworker-worker
mvn spring-boot:run
```

### 5. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The management console will be available at `http://localhost:3000`.

## API Endpoints

### Gateway
- `POST /api/v1/alerts/prometheus` — Submit Prometheus alert
- `POST /api/v1/alerts/json` — Submit JSON alert
- `POST /api/v1/channels/{id}/test` — Test message channel

### DataManager
- `GET/POST/PUT/DELETE /api/v1/api-keys`
- `GET/POST/PUT/DELETE /api/v1/channels`
- `GET/POST/PUT/DELETE /api/v1/assignees`
- `GET/POST/PUT/DELETE /api/v1/groups`
- `GET/POST/PUT/DELETE /api/v1/templates`
- `GET/POST/PUT/DELETE /api/v1/rules`
- `GET /api/v1/alerts/history`
- `GET /api/v1/alerts/delivery-logs`
- `GET /api/v1/alerts/failed`
- `GET /api/v1/alerts/dead-letters`
- `GET /api/v1/sse/alerts` — SSE real-time alert stream

## Authentication

All alert submission endpoints require the `X-API-Key` header. Rate limiting is enforced per API key (default: 100 requests/minute).

## Message Status Lifecycle

1. `recieved` — Gateway received the message
2. `GW_SENT` — Gateway enqueued to RabbitMQ
3. `DELIVERED` — Worker consumed from RabbitMQ
4. `WORKER_SENT` — Worker dispatched to message channel
5. `success` — Delivery confirmed
6. `FAILED` / `gw_failed` / `worker_failed` — Failure states

## Retry Policy

- Max 3 retries per alert rule (configurable)
- Exponential backoff: 5s → 10s → 20s
- After exhaustion, message is written to `dead_letter_message` table

## End-to-End Test

1. Create an API key via the frontend or DataManager API.
2. Create a DingDing message channel with a valid webhook URL.
3. Create a message template for `channel_type = DINGDING`.
4. Create an alert rule with `alert_type = JSON` and `match_key = service_type`.
5. Link the template and channel to the rule.
6. Send a test alert:

```bash
curl -X POST http://localhost:8081/api/v1/alerts/json \
  -H "X-API-Key: your-api-key-value" \
  -H "Content-Type: application/json" \
  -d '{"service_type": "demo", "title": "Test Alert", "content": "Hello from AlertWorker"}'
```
