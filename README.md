<div align="center">

<p align="center"><a href="#english">English</a> | <a href="#中文">中文</a></p>

<img src="docs/assets/alertworker-banner.svg" alt="AlertWorker" width="660">

<h3>One inbox for every alert. One pipeline to every channel.</h3>

</div>

---

## English

### What is AlertWorker?

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

### Why teams adopt it

| Problem without it | What AlertWorker changes |
| --- | --- |
| Every system builds and maintains its own webhook / SMTP code | One HTTP endpoint for all alert sources |
| Recipients hardcoded in each tool | Routing lives in rules, groups and templates — change it without a deploy |
| Provider-specific message formatting scattered everywhere | Channel-agnostic Jinja templates rendered per channel |
| Alerts silently lost on a hiccup | Durable queue with retries and a dead-letter inbox |
| No idea what was sent, to whom, or when | Full alert history, delivery logs and audit trail |
| Alert storms drown the important signal | Silence switches, rule matching and per-key rate limiting |

### Features

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

### Getting started

See [`docs/deploy`](docs/deploy) for installation and deployment guides, and
[`.claude/CLAUDE.md`](CLAUDE.md) if you want to contribute to the codebase.

---

## 中文

### AlertWorker 是什么？

AlertWorker 是一个**可私有化部署的告警通知枢纽**。它接收来自任意系统
（Prometheus Alertmanager、自定义 JSON、脚本、CI 任务）的告警，并按照你在
Web 控制台配置的模板与路由规则，把消息投递到团队真正会看的渠道 ——
**钉钉** 和 **邮件**。

可以把它看成一个**中转站**：各方把告警送进来，AlertWorker 决定谁收什么、
走哪个渠道，并确保送达。

```
 告警来源  ──▶ ┌─────────────────┐ ──▶ 钉钉
 （Prometheus、│   AlertWorker   │ ──▶ 邮件
  JSON、脚本）  │  路由 • 渲染     │ ──▶ …更多渠道
               │  重试 • 审计     │
               └─────────────────┘
```

### 引入它带来的好处

| 没有它时的问题 | AlertWorker 带来的改变 |
| --- | --- |
| 每个系统都要自己实现并维护 webhook / SMTP 代码 | 所有告警来源共用一个 HTTP 接入点 |
| 接收人写死在各个工具里 | 路由由规则、通信组和模板决定，改配置无需发版 |
| 各家渠道的消息格式散落各处 | 渠道无关的 Jinja 模板，按渠道渲染 |
| 一有抖动告警就悄悄丢失 | 持久化队列 + 重试 + 死信收件箱 |
| 不知道发了什么、发给了谁、什么时候发的 | 完整的告警历史、投递日志与审计轨迹 |
| 告警风暴淹没真正重要的信号 | 静默开关、规则匹配与按 API Key 限流 |

### 功能

- **多来源接入** —— 通过统一的 HTTP API 接收 Prometheus 与 JSON 告警，使用 API Key 鉴权。
- **基于规则的路由** —— 按 Prometheus 标签或 JSON key 匹配，可分发到多个通信组。
- **接收人管理** —— 接收人包含手机号、钉钉账号和中文姓名；可分组，也可按 `service_type` 路由。
- **可复用模板** —— Jinjava（兼容 Jinja2）模板，支持必填与可选槽位，并提供默认模板兜底；渠道在模板与规则绑定时指定。
- **多渠道投递** —— 钉钉（ActionCard / markdown / 文本）与邮件（HTML / 纯文本），通用分发器让新增渠道更简单。
- **可靠性设计** —— 消息经 RabbitMQ 流转，失败按指数退避重试（默认 5s → 10s → 20s），重试耗尽后写入死信表。
- **静默与降噪** —— 可暂停某条规则并保留被抑制的记录；未匹配的告警直接丢弃并上报，而不是无限重试。
- **限流保护** —— 按 API Key 的令牌桶限流（默认 100 条/分钟）。
- **实时控制台** —— 通过 SSE 实时查看告警流，并提供历史、投递日志、失败消息看板与死信查看/重试。
- **一键测试** —— 在依赖某个渠道之前，先验证它能否连通外部、能否送达指定人员。
- **全程可审计** —— 每一次配置变更与告警处理事件都有日志。
- **易于迁移** —— 支持 PostgreSQL 或 MySQL，使用 Nacos 作为配置中心，Docker Compose 快速本地启动。

### 快速开始

安装与部署指南见 [`docs/deploy`](docs/deploy)；如果希望参与代码贡献，
请先阅读 [`.claude/CLAUDE.md`](CLAUDE.md)。
