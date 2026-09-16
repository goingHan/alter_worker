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
| `alert_rule` | ~dozens | name, alert_type, match_key, match_value, retry_max, retry_backoff |
| `rule_group` | junction | rule_id + group_id (unique) |
| `alert_rule_template` | junction | rule_id + channel_type (unique), template_id |
| `alert_message` | high volume | payload, status, sub_status, retry_count |
| `dead_letter_message` | low | alert_message_id (unique), error_info |
| `audit_log` | high volume | action, entity_type, entity_id, operator, detail |
| `dictionary` | ~few | dict_type + dict_key (unique), dict_value |

## Key Columns

- **Soft delete**: All main entities have `deleted SMALLINT DEFAULT 0` (1 = deleted)
- **ID strategy**: `BIGSERIAL` / MyBatis-Plus `id-type: auto`
- **Indexes**: `alert_message(status)`, `alert_message(api_key_id)`, `alert_message(created_at)`, `audit_log(entity_type, entity_id)`, `audit_log(created_at)`, `message_template(channel_type)`, `alert_rule(alert_type)`

## Migration

- **Tool**: Flyway 10.11
- **Location**: `src/main/resources/db/migration/`
- **Current**: `V1__init_schema.sql` (all 13 tables in one migration)
- **Note**: All three backend services run Flyway by default. In production, disable on gateway and worker.

## Jinjava Templates

Templates stored in `message_template.content` use Jinjava syntax:
```jinjava
{{ alert.title }}  → context key from parsed alert
{{ recipient.cn_name }} → recipient's Chinese name
```
