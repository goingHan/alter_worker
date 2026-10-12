-- V11 (MySQL): DB-backed scheduler (scheduled_job), daily report config (daily_report + joins),
-- rule report-only mode, message type column, and built-in channel-test templates.

-- (a) rule "report_only" mode: matching alerts are stored as REPORT_PENDING (no MQ)
ALTER TABLE alert_rule ADD COLUMN report_only TINYINT(1) NOT NULL DEFAULT 0;

-- (b) alert_message.message_type: ALERT / TEST / REPORT (unified history).
ALTER TABLE alert_message ADD COLUMN message_type VARCHAR(16) NOT NULL DEFAULT 'ALERT';
UPDATE alert_message SET message_type = 'ALERT' WHERE message_type IS NULL;

-- (c) claim marker for the daily-report aggregation + supporting index
ALTER TABLE alert_message ADD COLUMN report_message_id BIGINT;
CREATE INDEX idx_alert_message_report_pending
    ON alert_message (status, report_message_id, alert_rule_id, created_at);

-- (d) TEST/REPORT messages have no API key. Must stay LAST among the alert_message changes.
ALTER TABLE alert_message MODIFY COLUMN api_key_id BIGINT NULL;

-- (e) built-in templates are seeded by migration and read-only in the UI.
ALTER TABLE message_template ADD COLUMN builtin TINYINT(1) NOT NULL DEFAULT 0;

-- (f) DB-backed scheduler: one row per scheduled job (periodic test / daily report).
CREATE TABLE IF NOT EXISTS scheduled_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    config_json LONGTEXT,
    last_run_at DATETIME,
    next_run_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- (g) daily report configuration (name, once-per-day cron, template, linked scheduled_job).
CREATE TABLE IF NOT EXISTS daily_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    template_id BIGINT,
    scheduled_job_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_daily_report_template FOREIGN KEY (template_id) REFERENCES message_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- (h) report -> rules whose REPORT_PENDING alerts get aggregated into the report.
CREATE TABLE IF NOT EXISTS daily_report_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_report_rule (report_id, rule_id),
    CONSTRAINT fk_drr_report FOREIGN KEY (report_id) REFERENCES daily_report (id) ON DELETE CASCADE,
    CONSTRAINT fk_drr_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- (i) report recipients: assignee + channel type (mirrors rule->assignee model).
CREATE TABLE IF NOT EXISTS daily_report_recipient (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_report_recipient (report_id, assignee_id, channel_type),
    CONSTRAINT fk_drp_report FOREIGN KEY (report_id) REFERENCES daily_report (id) ON DELETE CASCADE,
    CONSTRAINT fk_drp_assignee FOREIGN KEY (assignee_id) REFERENCES assignee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- (j) report -> explicit channel bindings (fallback to per-type default channel).
CREATE TABLE IF NOT EXISTS daily_report_channel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_report_channel (report_id, channel_id),
    CONSTRAINT fk_drc_report FOREIGN KEY (report_id) REFERENCES daily_report (id) ON DELETE CASCADE,
    CONSTRAINT fk_drc_channel FOREIGN KEY (channel_id) REFERENCES message_channel (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- (k) built-in channel-test templates (read-only, rendered by the Worker for TEST messages).
INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted)
SELECT '内置-通道测试模板 (DingDing)', 'DINGDING',
       '### 通道测试通知

- 通道：{{ channelName }}
- 时间：{{ testTime | fmt_time }}',
       0, 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = 1 AND channel_type = 'DINGDING' AND deleted = 0
);

INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted)
SELECT '内置-通道测试模板 (Email)', 'EMAIL',
       '<p>通道：{{ channelName }}</p><p>时间：{{ testTime | fmt_time }}</p>',
       0, 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = 1 AND channel_type = 'EMAIL' AND deleted = 0
);
