-- V10 (MySQL): alerts that matched no alert rule are dropped at the gateway (no recipients, no RabbitMQ) and
-- recorded as status = 'UNMATCHED'. The dashboard lists them by time, same shape as the SILENCED panel (V9).
-- PostgreSQL partial index replaced by an equivalent composite index.
CREATE INDEX idx_alert_message_unmatched ON alert_message (status, created_at);
