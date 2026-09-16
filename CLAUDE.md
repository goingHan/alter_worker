# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AlertWorker is a transfer station that accepts alert messages and transfers them to notification channels such as DingDing and Email.

## Architecture

### Technology Stack

##### frontend
- vue + iview + GSAP
- The design style is simple and cozy, using soft breathing tones for the color scheme
- Apply motion effects only where necessary
- Real-time updates via **Server-Sent Events (SSE)**

##### backend
- java + springboot + mybatis plus
- use postgres databases
- use the rabbitmq message queue
- use **nacos** as configuration center
- template engine: **Jinjava**

### Modules

- `alertworker-common`: Shared DTOs, enums, exceptions, utilities
- `alertworker-datamanager`: Stateless configuration/CRUD API (can run independently)
- `alertworker-gateway`: Alert ingestion, API key validation, rule matching, enqueueing
- `alertworker-worker`: RabbitMQ consumer, generic dispatcher for all message channels

### System Terms

- **API Key**: Required for sending alert messages. Attributes include `name` and `value`. Rate limiting is enforced **per API key** (default: 100 alerts/minute).

- **Message Channel**: The notification tools which can send message to user.
   - DingDing message: send message to dingding groups via dingding webhook robot.
   - Email message: send message via SMTP, supporting both HTML and plain text formats.

- **Assignee**: The assignee is responsible for receiving messages, who has many attributes:
   - phone number
   - DingDing username
   - The CN name

- **Communication Group**: The group contains many assignees. It also includes a send type attribute which can determine the message recipients.
   - When send type is `service_type`, we can configure which `service_type` can be received by which personnel, as well as the notification method of each person.

- **Message Template**: The template has many slots and fixed words.
   - Use **Jinjava** syntax (Jinja2-compatible for Java)
   - Includes required attributes and optional attributes
   - Templates are **channel-agnostic** (`channel_type` is optional); the channel is decided when binding a template to a rule (`alert_rule_template.channel_type`)
   - There is a **default template** that can be used as fallback when no rule-specific template is configured

- **Alert Rule**: A toolchain that specifies the alert format type (prometheus or json), the template to be applied and the corresponding communication group.
   - Can configure multiple communication groups
   - Has an `alertType` attribute: `PROMETHEUS` or `JSON`
   - **Matching logic**: When `alertType` is `PROMETHEUS`, match by label; when `JSON`, match by `key_name`
   - Retry and dead-letter strategy can be configured per rule
   - **Default retry policy**: max 3 retries, exponential backoff (5s -> 10s -> 20s)
   - After all retries are exhausted, the message is written to the **dead_letter_message** table in PostgreSQL
   - Can configure multiple templates for different **channel_types** via the `alert_rule_template` join table

### High-Level Flow

1. **DataManager** presents data to the frontend. This service can run independently and is stateless.

2. **MessageGateway** receives messages, handles them, and performs channel testing:
   - **Receive and handle message**
      - Validate the incoming API key and enforce rate limiting
      - Extract information from various data formats (Prometheus format, JSON)
      - Clean text
      - Determine who should receive the message based on the alert rule (`alertType` + label/key_name matching)
      - Determine `service_type` from the incoming alert (Prometheus label or JSON key_name)
      - Filter assignees by `service_type` and notification method preference
      - Always enqueue the prepared message to RabbitMQ (never send directly)
      - The RabbitMQ payload includes the raw alert context, `alert_rule_id`, and the resolved recipient list
   - **Test the message channel** whether it can send messages to the outside and whether it can send messages to somebody

3. **Workers** (single generic worker type) receive messages from MQ and send them to customers:
   - Consume message from RabbitMQ
   - Re-query the database for the AlertRule, templates, and channel configuration
   - Render the appropriate template (by channel_type, falling back to the default template if needed)
   - Dispatch via the configured Message Channel (DingDing or Email)
   - Update the message status in DB

### Message Status Lifecycle

Message statuses (both Gateway and Worker can transition status):
- `recieved`: Gateway received the message
- `GW_SENT`: Gateway successfully sent the message to MQ
- `DELIVERED`: Worker received the message from MQ
- `WORKER_SENT`: Worker sent the message to the Message Channel
- `FAILED`: Failed to handle message
- `success`: Successfully handled message

Sub-statuses:
- `gw_failed`: Gateway failed to handle message
- `worker_failed`: Worker failed to handle message

### Observability & Operations

- **Audit logs**: Track all configuration changes (admin CRUD) and alert handling events (gateway + worker)
- **Failed message dashboard**: Display messages that failed at any stage with full status trace, including dead-letter messages
- **Rate limiting / flood protection**: Per-API-key token bucket; future expansion to per-rule and per-channel limits

### Frontend Scope

- Configuration management: rules, templates, groups, assignees, channels, API keys
- Display real-time alert history (via SSE)
- Display delivery logs with full status trace
- Failed message dashboard with inspect/retry dead-letter capability
- **CodeMap**: 定位前端页面问题先看 `docs/design/codemap/frontend-codemap.md`（页面→文件→行号、症状→定位指南、已知问题清单），不要全量扫描代码

### JAVA Dictionary

Use dictionary tables for static KV pairs that rarely change. Create KV pairs as needed during development.

### Message Type

##### DingDing Message
DingDing messages include:
- **ActionCard**: Use the ActionCard message to send alert messages, as well as configure the parameters of action buttons.
- **markdown**: Send Markdown messages to groups.
- **text**: Send plain text messages to groups.

##### Email Message
Email messages include:
- **HTML**: Rich text email with HTML formatting.
- **plain text**: Simple text-only email.

##### FIND CLASS FILE
When looking for code files by business name, you can use the understand-anything plugin (command is:  /understand-anything:understand-chat ).