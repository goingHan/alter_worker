-- V8: explicit rule->channel binding, per-type default channel, actual channel names on messages

CREATE TABLE IF NOT EXISTS alert_rule_channel (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES message_channel(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_rule_channel UNIQUE (rule_id, channel_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_rule_channel_rule_id ON alert_rule_channel(rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_rule_channel_channel_id ON alert_rule_channel(channel_id);

ALTER TABLE message_channel ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- backfill: keep the legacy behaviour (first channel per type) as the explicit default of that type
UPDATE message_channel mc SET is_default = TRUE
WHERE mc.deleted = 0
  AND mc.id IN (
      SELECT DISTINCT ON (channel_type) id
      FROM message_channel
      WHERE deleted = 0
      ORDER BY channel_type, id
  );

ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS channel_names TEXT;
