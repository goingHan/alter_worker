-- V11: DB-backed scheduler (scheduled_job), daily report config (daily_report + joins),
-- rule report-only mode, message type column, and built-in channel-test templates.
--
-- Everything here is idempotent: columns/tables/indexes use IF NOT EXISTS, the seed
-- rows are guarded with WHERE NOT EXISTS.

-- (a) rule "report_only" mode: matching alerts are stored as REPORT_PENDING (no MQ)
--     and picked up by the daily report job instead of being delivered.
ALTER TABLE alert_rule ADD COLUMN IF NOT EXISTS report_only BOOLEAN NOT NULL DEFAULT FALSE;

-- (b) alert_message.message_type: ALERT / TEST / REPORT (unified history).
--     Add with a DEFAULT first, then backfill (safe with the DEFAULT, but keeps old
--     rows explicit and covers the ADD-without-fill edge case).
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS message_type VARCHAR(16) NOT NULL DEFAULT 'ALERT';
UPDATE alert_message SET message_type = 'ALERT' WHERE message_type IS NULL;

-- (c) claim marker for the daily-report aggregation + partial index for the
--     aggregation query (same partial-index style as V9).
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS report_message_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_alert_message_report_pending
    ON alert_message(alert_rule_id, created_at)
    WHERE status = 'REPORT_PENDING' AND report_message_id IS NULL;

-- (d) TEST/REPORT messages have no API key. Must stay LAST among the alert_message changes.
ALTER TABLE alert_message ALTER COLUMN api_key_id DROP NOT NULL;

-- (e) built-in templates are seeded by migration and read-only in the UI.
ALTER TABLE message_template ADD COLUMN IF NOT EXISTS builtin BOOLEAN NOT NULL DEFAULT FALSE;

-- (f) DB-backed scheduler: one row per scheduled job (periodic test / daily report).
CREATE TABLE IF NOT EXISTS scheduled_job (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_json TEXT,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

-- (g) daily report configuration (name, once-per-day cron, template, linked scheduled_job).
CREATE TABLE IF NOT EXISTS daily_report (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    template_id BIGINT REFERENCES message_template(id),
    scheduled_job_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

-- (h) report -> rules whose REPORT_PENDING alerts get aggregated into the report.
CREATE TABLE IF NOT EXISTS daily_report_rule (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_report(id) ON DELETE CASCADE,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_report_rule UNIQUE (report_id, rule_id)
);

-- (i) report recipients: assignee + channel type (mirrors rule->assignee model).
CREATE TABLE IF NOT EXISTS daily_report_recipient (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_report(id) ON DELETE CASCADE,
    assignee_id BIGINT NOT NULL REFERENCES assignee(id),
    channel_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_report_recipient UNIQUE (report_id, assignee_id, channel_type)
);

-- (j) report -> explicit channel bindings (fallback to per-type default channel).
CREATE TABLE IF NOT EXISTS daily_report_channel (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_report(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES message_channel(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_report_channel UNIQUE (report_id, channel_id)
);

-- (k) built-in channel-test templates (read-only, rendered by the Worker for TEST messages).
--     fmt_time is the custom filter registered in JinjavaTemplateService.
INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted)
SELECT '内置-通道测试模板 (DingDing)', 'DINGDING',
       '### 通道测试通知

- 通道：{{ channelName }}
- 时间：{{ testTime | fmt_time }}',
       FALSE, TRUE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = TRUE AND channel_type = 'DINGDING' AND deleted = 0
);

INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted)
SELECT '内置-通道测试模板 (Email)', 'EMAIL',
       '<p>通道：{{ channelName }}</p><p>时间：{{ testTime | fmt_time }}</p>',
       FALSE, TRUE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = TRUE AND channel_type = 'EMAIL' AND deleted = 0
);
