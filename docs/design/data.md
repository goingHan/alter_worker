<!-- Generated: 2026-07-14 | Files scanned: 1 (V1__init_schema.sql) | Token estimate: ~550 -->

# Database Schema

## Entity Relationship

```
api_key ──< alert_message >── alert_rule ──< rule_group >── communication_group
                                           │                    │
                                           └──< alert_rule_template >──┐
                                                                       │
                                           message_template <──────────┘

communication_group ──< group_assignee >── assignee

alert_message ──< dead_letter_message
```

## Tables

| Table | Rows/Key | Notes |
|---|---|---|
| `api_key` | ~few | name, api_value (unique), rate_limit (default 100) |
| `message_channel` | ~few | name, channel_type (DINGDING/EMAIL), config_json (webhook URL, SMTP) |
| `assignee` | ~dozens | phone, dingding_username, cn_name |
| `communication_group` | ~dozens | name, send_type (ALL/serivice_type) |
| `group_assignee` | junction | group_id + assignee_id + channel_type (unique), service_types (JSON) |
| `message_template` | ~dozens | name, channel_type (可选，渠道由规则绑定决定), content (Jinjava template), is_default |
| `alert_rule` | ~dozens | name, alert_type, match_key, match_value, retry_max, retry_backoff, silenced |
| `rule_group` | junction | rule_id + group_id (unique) |
| `alert_rule_template` | junction | rule_id + channel_type (unique), template_id |
| `alert_message` | high volume | payload, status, sub_status, retry_count |
| `dead_letter_message` | low | alert_message_id (unique), error_info |
| `audit_log` | high volume | action, entity_type, entity_id, operator, detail |
| `dictionary` | ~few | dict_type + dict_key (unique), dict_value |

## Key Columns

- **Soft delete**: All main entities have `deleted SMALLINT DEFAULT 0` (1 = deleted)
- **ID strategy**: `BIGSERIAL` / MyBatis-Plus `id-type: auto`
- **Rule silence**（2026-09-17 起）: `alert_rule.silenced BOOLEAN NOT NULL DEFAULT FALSE`；为 TRUE 时网关丢弃该规则匹配到的告警，落 `alert_message.status = 'SILENCED'`
- **Dropped messages**（2026-09-17 起）: 网关丢弃的告警用独立状态区分 —— `SILENCED`（命中静默规则，`alert_rule_id` 有值）与 `UNMATCHED`（未匹配任何规则，`alert_rule_id` 为 NULL）；两者都不算 `FAILED`
- **Indexes**: `alert_message(status)`, `alert_message(api_key_id)`, `alert_message(created_at)`, `audit_log(entity_type, entity_id)`, `audit_log(created_at)`, `message_template(channel_type)`, `alert_rule(alert_type)`, `alert_message(created_at DESC) WHERE status = 'SILENCED'`, `alert_message(created_at DESC) WHERE status = 'UNMATCHED'`

## Migration

- **Tool**: Flyway 10.11
- **Location**: `src/main/resources/db/migration/`
- **Current**: `V24__warning_digest_assignee_stats.sql`（V24：WARNING 汇总模板增加「按责任人」统计与明细责任人/内容列；V23：修复被 V18 覆盖成 markdown 的内置 HTML 日报模板；V22：`alert_rule.ignore_digest`；V21：WARNING 汇总）
- **Note**: All three backend services run Flyway by default. In production, disable on gateway and worker.

## Jinjava Templates

Templates stored in `message_template.content` use Jinjava syntax:
```jinjava
{{ alert.title }}  → context key from parsed alert
{{ recipient.cn_name }} → recipient's Chinese name
```
