-- V9: per-rule silence switch.
-- While silenced = TRUE, the gateway drops every alert matched by the rule (including test alerts):
-- recipients and channels are not resolved, nothing is enqueued to RabbitMQ, and only an
-- alert_message row with status = 'SILENCED' is persisted so the dashboard can show blocked messages.

ALTER TABLE alert_rule ADD COLUMN IF NOT EXISTS silenced BOOLEAN NOT NULL DEFAULT FALSE;

-- dashboard/history queries list silenced messages by time
CREATE INDEX IF NOT EXISTS idx_alert_message_silenced ON alert_message(created_at DESC) WHERE status = 'SILENCED';
