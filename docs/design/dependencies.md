<!-- Generated: 2026-07-14 | Files scanned: 74 | Token estimate: ~600 -->

# Dependencies

## External Services

| Service | Version | Role | Connection |
|---|---|---|---|
| **PostgreSQL** | 15 | Primary data store | JDBC (port 5432) |
| **RabbitMQ** | 3.12 | Message queue + DLQ | AMQP (port 5672) / HTTP management (15672) |
| **Nacos** | 2.3 | Configuration center | HTTP (port 8848) / gRPC (9848) |

## Third-Party Integrations

| Integration | Channel Type | Config |
|---|---|---|
| **DingDing Webhook** | DINGDING | Webhook URL in `message_channel.config_json`; optional HMAC sign via `config_json.secret` (keyword + IP allowlist + sign all supported) |
| **SMTP** | EMAIL | Configured via Nacos shared config (`spring.mail.*`) |

## Java Runtime Dependencies (Maven)

| Dependency | Version | Used By |
|---|---|---|
| Spring Boot | 3.2.5 | All backend modules |
| Spring Cloud Alibaba | 2022.0.0.0 | All backend modules (Nacos integration) |
| MyBatis-Plus | 3.5.6 | All backend modules (ORM) |
| Jinjava | 2.7.2 | Worker (template rendering) |
| Flyway | 10.11 | All backend modules (DB migrations) |
| Lombok | latest | All backend modules |
| PostgreSQL JDBC Driver | managed | All backend modules |

## Frontend Dependencies (npm)

| Dependency | Version | Purpose |
|---|---|---|
| Vue | ^3.4.21 | SPA framework |
| Vue Router | ^4.3.0 | Client-side routing |
| View UI Plus | ^1.3.16 | UI component library (iView) |
| GSAP | ^3.12.5 | Animation engine |
| Axios | ^1.6.8 | HTTP client |
| Vite | ^5.2.0 | Build tool + dev server |

## Shared Module Dependency

```
alertworker-common
    ├── alertworker-datamanager  (depends on)
    ├── alertworker-gateway      (depends on)
    └── alertworker-worker       (depends on)
```

## Port Map

| Port | Service | Protocol |
|---|---|---|
| 3000 | Frontend (Vite dev) | HTTP |
| 8080 | DataManager | HTTP |
| 8081 | Gateway | HTTP |
| 8082 | Worker | HTTP |
| 5432 | PostgreSQL | TCP |
| 5672 | RabbitMQ | AMQP |
| 15672 | RabbitMQ Management | HTTP |
| 8848 | Nacos API | HTTP |
| 9848 | Nacos gRPC | gRPC |
