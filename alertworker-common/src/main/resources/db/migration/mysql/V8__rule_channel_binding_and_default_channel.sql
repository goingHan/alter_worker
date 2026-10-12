-- V8 (MySQL): explicit rule->channel binding, per-type default channel, actual channel names on messages

CREATE TABLE IF NOT EXISTS alert_rule_channel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_rule_channel (rule_id, channel_id),
    KEY idx_alert_rule_channel_rule_id (rule_id),
    KEY idx_alert_rule_channel_channel_id (channel_id),
    CONSTRAINT fk_arc_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT fk_arc_channel FOREIGN KEY (channel_id) REFERENCES message_channel (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE message_channel ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0;

-- backfill: keep the legacy behaviour (first channel per type) as the explicit default of that type.
-- Replaces PostgreSQL's DISTINCT ON with a MIN(id) derived table.
UPDATE message_channel mc
JOIN (
    SELECT channel_type, MIN(id) AS keep_id
    FROM message_channel
    WHERE deleted = 0
    GROUP BY channel_type
) t ON mc.id = t.keep_id
SET mc.is_default = 1;

ALTER TABLE alert_message ADD COLUMN channel_names LONGTEXT;
