# AlertWorker Deployment Guide

Deploy AlertWorker in test and production environments. Covers configuration, Docker image building, and orchestration with Docker Compose.

## Architecture at a Glance

| Service | Port | URL Prefix | Purpose |
|---|---|---|---|
| `alertworker-datamanager` | 8083 | `/pw/alertworker/dm` | Stateless CRUD API, configuration frontend |
| `alertworker-gateway` | 8081 | `/pw/alertworker/gw` | Alert ingestion, API key validation, rule matching, MQ enqueue |
| `alertworker-worker` | 8082 | `/pw/alertworker/worker` | RabbitMQ consumer, template rendering, channel dispatch |
| `frontend` | 3000 | — | Vue 3 management console |
| PostgreSQL | 5432 | — | Primary database |
| RabbitMQ | 5672 / 15672 | — | Message broker + management UI |
| Nacos | 8848 / 9848 | — | Configuration center |

> URL 结构 = **工程前缀 `/pw/alertworker`**（用于与其他工程区分）+ **模块前缀**（datamanager=`/dm`、gateway=`/gw`、worker=`/worker`，由各应用 `server.servlet.context-path` 实现），从 URL 即可区分工程与应用。

All three backend services depend on PostgreSQL, RabbitMQ, and Nacos. The frontend proxies API calls to `alertworker-datamanager` (port 8083).

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+ and npm
- Docker & Docker Compose (for infrastructure and containerized deployment)
- Git

---

## Configuration Guide

AlertWorker uses **Nacos** as its configuration center. Each service pulls a shared config (`alertworker-shared.yaml`) from Nacos at startup, and overrides local settings via environment variables.

### Nacos Shared Config

File: [`nacos-shared-config.yaml`](../../nacos-shared-config.yaml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/alertworker
    username: alertworker
    password: alertworker
    driver-class-name: org.postgresql.Driver
  mail:
    host: smtp.example.com
    port: 587
    username: alerts@example.com
    password: your-email-password
    protocol: smtp
    default-encoding: UTF-8

alertworker:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: alertworker
    password: alertworker
    virtual-host: /
    exchange: alertworker.exchange
    routing-key: alertworker.routing.key
    queue: alertworker.queue
    dlq: alertworker.dlq
    dlx: alertworker.dlx
```

**What to change per environment:**

| Field | Test | Production |
|---|---|---|
| `spring.datasource.url` | `127.0.0.1:5432/alertworker` | Internal RDS or DB host |
| `spring.datasource.password` | `alertworker` | Strong random password |
| `spring.mail.host` | `smtp.example.com` | Your SMTP relay |
| `spring.mail.password` | Dev password | Production SMTP credentials |
| `alertworker.rabbitmq.host` | `127.0.0.1` | Internal RabbitMQ host |
| `alertworker.rabbitmq.password` | `alertworker` | Strong random password |

### Environment Variable Overrides

Each service's `application.yml` reads these environment variables with sensible defaults:

| Variable | Default | Services | Description |
|---|---|---|---|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | all backend | Nacos server address |
| `NACOS_NAMESPACE` | _(empty)_ | all backend | Nacos namespace ID for isolation |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/alertworker` | all backend | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `alertworker` | all backend | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `alertworker` | all backend | DB password |

### Database Migrations

All three backend services run **Flyway** migrations on startup:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

Migrations live in each module's `src/main/resources/db/migration/` directory. Because all services share the same database, only one service should run migrations in production — configure `spring.flyway.enabled: false` on the others.

---

## Test Environment Deployment

Test environment runs the infrastructure in Docker and the application services directly via Maven.

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts PostgreSQL, RabbitMQ, and Nacos. Verify they are healthy:

```bash
docker-compose ps
```

You should see all three containers in `Up` or `healthy` state.

### 2. Import Nacos Shared Config

1. Open Nacos console at `http://localhost:8848/nacos` (no auth required in default config).
2. Navigate to **Configuration Management → Configuration List**.
3. Click the **+** button (or import via the Import button).
4. Set Data ID: `alertworker-shared.yaml`
5. Set Group: `DEFAULT_GROUP`
6. Set Format: `YAML`
7. Paste the contents of [`nacos-shared-config.yaml`](../../nacos-shared-config.yaml).
8. Click **Publish**.

Alternatively, use the Nacos HTTP API:

```bash
curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  -d 'dataId=alertworker-shared.yaml&group=DEFAULT_GROUP&content='"$(cat nacos-shared-config.yaml | jq -sRr @uri)"
```

### 3. Build Backend Modules

```bash
# From the project root
mvn clean install -DskipTests
```

This compiles all four modules (`alertworker-common`, `alertworker-datamanager`, `alertworker-gateway`, `alertworker-worker`) in order.

### 4. Start Backend Services

Start each module in its own terminal:

```bash
# DataManager — Configuration CRUD API (port 8080)
cd alertworker-datamanager
mvn spring-boot:run

# Gateway — Alert ingestion (port 8081)
cd alertworker-gateway
mvn spring-boot:run

# Worker — MQ consumer & channel dispatcher (port 8082)
cd alertworker-worker
mvn spring-boot:run
```

Or start all three in sequence as background processes:

```bash
# Linux/Mac
mvn spring-boot:run -pl alertworker-datamanager &
mvn spring-boot:run -pl alertworker-gateway &
mvn spring-boot:run -pl alertworker-worker &
```

> **Windows users:** Use separate terminals or run each with `start mvn spring-boot:run -pl alertworker-datamanager`.

### 5. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The management console is now available at `http://localhost:3000`.

The Vite dev server proxies `/api` requests to `http://localhost:8080` (DataManager). See [`frontend/vite.config.js`](../../frontend/vite.config.js):

```js
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

---

## Production Environment Deployment

### Build Docker Images

Create a `Dockerfile` for each backend module. Below are the recommended Dockerfiles.

#### Backend Dockerfile (shared pattern)

Place this as `Dockerfile` in the project root. It builds any module via build arguments.

```dockerfile
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY alertworker-common/pom.xml alertworker-common/
COPY alertworker-datamanager/pom.xml alertworker-datamanager/
COPY alertworker-gateway/pom.xml alertworker-gateway/
COPY alertworker-worker/pom.xml alertworker-worker/

# Pre-download dependencies (layer caching)
RUN mvn dependency:go-offline -B

COPY . .
RUN mvn clean package -DskipTests -pl alertworker-datamanager -am

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S alertworker && adduser -S alertworker -G alertworker

COPY --from=builder /build/alertworker-datamanager/target/*.jar app.jar

USER alertworker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build the image:

```bash
# DataManager (port 8080)
docker build \
  --build-arg MODULE=alertworker-datamanager \
  --build-arg PORT=8080 \
  -t alertworker-datamanager:latest \
  -f Dockerfile .

# Gateway (port 8081)
# Change --build-arg MODULE=alertworker-gateway and PORT=8081
# Worker (port 8082)
# Change --build-arg MODULE=alertworker-worker and PORT=8082
```

#### Per-Module Dockerfile (simpler alternative)

Each module gets its own minimal Dockerfile:

**alertworker-datamanager/Dockerfile:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S alertworker && adduser -S alertworker -G alertworker
COPY target/alertworker-datamanager-*.jar app.jar
USER alertworker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build from the project root:

```bash
mvn clean package -DskipTests

docker build -t alertworker-datamanager:latest alertworker-datamanager
docker build -t alertworker-gateway:latest alertworker-gateway
docker build -t alertworker-worker:latest alertworker-worker
```

#### Frontend Dockerfile

**frontend/Dockerfile:**

```dockerfile
# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve with nginx
FROM nginx:stable-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**frontend/nginx.conf:**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # gateway：告警接入 / 死信重试 / 通道测试（/pw/alertworker/gw）
    location /pw/alertworker/gw/ {
        proxy_pass http://alertworker-gateway:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # datamanager：配置 CRUD / 告警历史（/pw/alertworker/dm）
    location /pw/alertworker/dm/ {
        proxy_pass http://alertworker-datamanager:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Build:

```bash
docker build -t alertworker-frontend:latest frontend
```

### Push Images to Registry

```bash
docker tag alertworker-datamanager:latest registry.example.com/alertworker-datamanager:1.0.0
docker push registry.example.com/alertworker-datamanager:1.0.0

# Repeat for gateway, worker, and frontend
```

### Full Docker Compose (Production)

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

networks:
  alertworker-net:
    driver: bridge

volumes:
  postgres_data:
  rabbitmq_data:
  nacos_data:

services:
  postgres:
    image: postgres:15-alpine
    container_name: alertworker-postgres
    environment:
      POSTGRES_DB: alertworker
      POSTGRES_USER: alertworker
      POSTGRES_PASSWORD: ${DB_PASSWORD:-alertworker}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - alertworker-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alertworker -d alertworker"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: alertworker-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: alertworker
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-alertworker}
    ports:
      - "5672:5672"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    networks:
      - alertworker-net
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: alertworker-nacos
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: postgresql
      NACOS_AUTH_ENABLE: ${NACOS_AUTH_ENABLE:-false}
    ports:
      - "8848:8848"
    volumes:
      - nacos_data:/home/nacos/data
    networks:
      - alertworker-net
    restart: unless-stopped

  datamanager:
    image: alertworker-datamanager:latest
    container_name: alertworker-datamanager
    build:
      context: .
      dockerfile: alertworker-datamanager/Dockerfile
    environment:
      NACOS_SERVER_ADDR: nacos:8848
      NACOS_NAMESPACE: ${NACOS_NAMESPACE:-}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alertworker
      SPRING_DATASOURCE_USERNAME: alertworker
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-alertworker}
      # Only this service runs Flyway migrations in production
      SPRING_FLYWAY_ENABLED: "true"
    ports:
      - "8080:8080"
    networks:
      - alertworker-net
    depends_on:
      postgres:
        condition: service_healthy
      nacos:
        condition: service_started
    restart: unless-stopped

  gateway:
    image: alertworker-gateway:latest
    container_name: alertworker-gateway
    build:
      context: .
      dockerfile: alertworker-gateway/Dockerfile
    environment:
      NACOS_SERVER_ADDR: nacos:8848
      NACOS_NAMESPACE: ${NACOS_NAMESPACE:-}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alertworker
      SPRING_DATASOURCE_USERNAME: alertworker
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-alertworker}
      SPRING_FLYWAY_ENABLED: "false"
    ports:
      - "8081:8081"
    networks:
      - alertworker-net
    depends_on:
      postgres:
        condition: service_healthy
      nacos:
        condition: service_started
      datamanager:
        condition: service_started
    restart: unless-stopped

  worker:
    image: alertworker-worker:latest
    container_name: alertworker-worker
    build:
      context: .
      dockerfile: alertworker-worker/Dockerfile
    environment:
      NACOS_SERVER_ADDR: nacos:8848
      NACOS_NAMESPACE: ${NACOS_NAMESPACE:-}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alertworker
      SPRING_DATASOURCE_USERNAME: alertworker
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-alertworker}
      SPRING_FLYWAY_ENABLED: "false"
    networks:
      - alertworker-net
    depends_on:
      postgres:
        condition: service_healthy
      nacos:
        condition: service_started
    restart: unless-stopped

  frontend:
    image: alertworker-frontend:latest
    container_name: alertworker-frontend
    build:
      context: frontend
    ports:
      - "3000:80"
    networks:
      - alertworker-net
    depends_on:
      - datamanager
    restart: unless-stopped
```

### Deploy with Docker Compose

```bash
# Set production passwords
export DB_PASSWORD="your-strong-db-password"
export RABBITMQ_PASSWORD="your-strong-rabbitmq-password"

# Start all services
docker-compose -f docker-compose.prod.yml up -d

# Check status
docker-compose -f docker-compose.prod.yml ps

# Tail logs
docker-compose -f docker-compose.prod.yml logs -f
```

### Nacos Bootstrap for Docker Deployments

In Docker Compose, Nacos starts before the application services, but the shared config (`alertworker-shared.yaml`) must be published to Nacos before any service can start.

**Option A: Pre-seed via Nacos HTTP API (recommended)**

Add an init container to the compose file:

```yaml
init-nacos-config:
  image: curlimages/curl:latest
  container_name: alertworker-init-nacos
  networks:
    - alertworker-net
  depends_on:
    nacos:
      condition: service_started
  entrypoint:
    - /bin/sh
    - -c
    - |
      echo "Waiting for Nacos..."
      sleep 15
      echo "Publishing shared config..."
      curl -s -X POST 'http://nacos:8848/nacos/v1/cs/configs' \
        -d 'dataId=alertworker-shared.yaml&group=DEFAULT_GROUP&content=spring.datasource.url: jdbc:postgresql://postgres:5432/alertworker'
      echo "Nacos config published."
```

**Option B: Manual import (used for first deployment)**

After starting the infrastructure, manually import the config via the Nacos web console at `http://host-ip:8848/nacos`, then restart the application services.

---

## Environment-Specific Configuration Summary

| Aspect | Test | Production |
|---|---|---|
| Infrastructure | Docker Compose on dev machine | Dedicated hosts or container orchestration |
| Backend services | `mvn spring-boot:run` | Docker containers |
| Frontend | `npm run dev` (hot-reload) | Docker container with nginx |
| Database password | `alertworker` | Strong random, stored in secrets manager |
| RabbitMQ password | `alertworker` | Strong random, stored in secrets manager |
| Nacos auth | Disabled | Enable with `NACOS_AUTH_ENABLE=true` |
| SMTP | Dev/sink server | Production SMTP relay |
| Flyway migrations | All services enabled | Only `datamanager` runs migrations |
| Frontend proxy | Vite dev proxy to `localhost:8080` | nginx proxy to `datamanager:8080` |

---

## Verification

### Health Checks

Each backend service exposes Spring Boot actuator endpoints (if enabled):

```bash
curl http://localhost:8083/pw/alertworker/dm/actuator/health      # DataManager
curl http://localhost:8081/pw/alertworker/gw/actuator/health      # Gateway
curl http://localhost:8082/pw/alertworker/worker/actuator/health  # Worker
```

### End-to-End Test

1. Create an API key via the frontend or DataManager API.
2. Create a DingDing message channel with a valid webhook URL.
3. Create a message template for `channel_type = DINGDING`.
4. Create an alert rule with `alert_type = JSON` and `match_key = service_type`.
5. Link the template and channel to the rule.
6. Send a test alert:

```bash
curl -X POST http://localhost:8081/pw/alertworker/gw/api/v1/alerts/json \
  -H "X-API-Key: your-api-key-value" \
  -H "Content-Type: application/json" \
  -d '{"service_type": "demo", "title": "Test Alert", "content": "Hello from AlertWorker"}'
```

### Check Message Lifecycle

| Status | Meaning | Where to check |
|---|---|---|
| `recieved` | Gateway accepted the message | DataManager API / frontend |
| `GW_SENT` | Gateway enqueued to RabbitMQ | RabbitMQ management console (`localhost:15672`) |
| `DELIVERED` | Worker consumed from MQ | Worker logs |
| `WORKER_SENT` | Worker dispatched to channel | Worker logs |
| `success` | Delivery successful | DataManager API / frontend |
| `FAILED` | Something went wrong | Frontend failed message dashboard |

---

## Upgrading

1. **Pull latest code:** `git pull`
2. **Rebuild:** `mvn clean package -DskipTests`
3. **Rebuild Docker images:** `docker-compose -f docker-compose.prod.yml build`
4. **Rolling restart:** `docker-compose -f docker-compose.prod.yml up -d`

Nacos configuration changes are picked up at runtime (the shared config has `refreshEnabled=true`). No restart needed for config-only changes.

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| Service won't start, Nacos connection refused | Nacos not ready | Wait for Nacos health check, increase `depends_on` sleep |
| Nacos config not found | `alertworker-shared.yaml` not published | Import config via Nacos console |
| DB connection failed | Wrong credentials or host | Check `SPRING_DATASOURCE_*` env vars |
| Flyway migration error | Multiple services racing | Disable Flyway on all but one service |
| Gateway returns 401 | Missing or invalid `X-API-Key` header | Create API key via DataManager |
| Worker not consuming | RabbitMQ connection config wrong | Check MQ host/password in Nacos config |
| Frontend shows blank page | API proxy misconfigured | Check nginx proxy_pass target |
