-- V10: alerts that matched no alert rule are dropped at the gateway (no recipients, no RabbitMQ) and recorded as
-- status = 'UNMATCHED'. The dashboard lists them by time, same shape as the SILENCED panel added in V9.

CREATE INDEX IF NOT EXISTS idx_alert_message_unmatched
    ON alert_message(created_at DESC) WHERE status = 'UNMATCHED';
