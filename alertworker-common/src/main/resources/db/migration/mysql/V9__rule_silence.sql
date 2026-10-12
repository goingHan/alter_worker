-- V9 (MySQL): per-rule silence switch.
-- While silenced = 1, the gateway drops every alert matched by the rule (including test alerts):
-- recipients and channels are not resolved, nothing is enqueued to RabbitMQ, and only an
-- alert_message row with status = 'SILENCED' is persisted.

ALTER TABLE alert_rule ADD COLUMN silenced TINYINT(1) NOT NULL DEFAULT 0;

-- PostgreSQL used a partial index (created_at DESC) WHERE status = 'SILENCED'; MySQL has no partial
-- indexes, so use an equivalent composite index (status, created_at).
CREATE INDEX idx_alert_message_silenced ON alert_message (status, created_at);
