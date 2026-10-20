<div align="center">

<p align="center"><strong>English</strong> | <a href="README.zh-CN.md">中文</a></p>

<img src="docs/assets/alertworker-banner.svg" alt="AlertWorker" width="660">

<h3>One inbox for every alert. One pipeline to every channel.</h3>

</div>

---

## What is AlertWorker?

AlertWorker is a **self-hosted alert notification hub**. It accepts alerts from
any system (Prometheus Alertmanager, custom JSON, scripts, CI jobs) and delivers
them to the channels your team actually reads — **DingDing** and **Email** —
through templates and routing rules you configure in a web console.

Think of it as a **transfer station**: systems send, AlertWorker decides who gets
what, on which channel, and makes sure it lands.

```
 Alert Source ──▶ ┌─────────────────┐ ──▶ DingDing
 (Prometheus,     │   AlertWorker   │ ──▶ Email
  JSON, scripts)  │  route • render │ ──▶ ...more channels
                  │  retry • audit  │
                  └─────────────────┘
```

## Why teams adopt it

| Problem without it | What AlertWorker changes |
| --- | --- |
| Every system builds and maintains its own webhook / SMTP code | One HTTP endpoint for all alert sources |
| Recipients hardcoded in each tool | Routing lives in rules, groups and templates — change it without a deploy |
| Provider-specific message formatting scattered everywhere | Channel-agnostic Jinja templates rendered per channel |
| Alerts silently lost on a hiccup | Durable queue with retries and a dead-letter inbox |
| No idea what was sent, to whom, or when | Full alert history, delivery logs and audit trail |
| Alert storms drown the important signal | Silence switches, rule matching and per-key rate limiting |

## Features

- **Multi-source ingestion** — Prometheus and JSON alerts over a single HTTP API, secured by API keys.
- **Rule-based routing** — match by Prometheus label or JSON key, fan out to multiple communication groups.
- **Recipient management** — assignees with phone, DingDing account and CN name; group them, or route by `service_type`.
- **Reusable templates** — Jinjava (Jinja2-compatible) templates with required and optional slots, plus a default fallback; the channel is chosen when you bind a template to a rule.
- **Multi-channel delivery** — DingDing (ActionCard / markdown / text) and Email (HTML / plain text), with a generic dispatcher that makes new channels easy to add.
- **Reliable by design** — messages go through RabbitMQ, retry with exponential backoff (default 5s → 10s → 20s), and land in a dead-letter table when exhausted.
- **Silence & noise control** — pause a rule while keeping a record of what was suppressed; unmatched alerts are dropped and reported instead of retrying forever.
- **Rate limiting** — token-bucket throttling per API key (default 100 alerts/minute).
- **Real-time console** — live alert stream over SSE, plus history, delivery logs, a failed-message dashboard and dead-letter inspection/retry.
- **Test in one click** — verify a channel can reach the outside world and reach a specific person before you rely on it.
- **Auditable** — every configuration change and alert handling event is logged.
- **Portable** — PostgreSQL or MySQL, Nacos for configuration, Docker Compose to get running locally.

## Getting started

See [`docs/deploy`](docs/deploy) for installation and deployment guides, and
[`CLAUDE.md`](CLAUDE.md) if you want to contribute to the codebase.
