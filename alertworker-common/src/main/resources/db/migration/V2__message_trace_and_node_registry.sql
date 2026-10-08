-- V2: message traceability (unique message_no, raw payload, timing columns) + node registry
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS message_no VARCHAR(64);
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS raw_payload TEXT;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS received_at TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS gw_sent_at TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS worker_received_at TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS worker_sent_at TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_message_no ON alert_message(message_no);
CREATE INDEX IF NOT EXISTS idx_alert_message_received_at ON alert_message(received_at);

UPDATE alert_message SET message_no = 'AW-' || id WHERE message_no IS NULL;
UPDATE alert_message SET received_at = created_at WHERE received_at IS NULL;

CREATE TABLE IF NOT EXISTS node_registry (
    id BIGSERIAL PRIMARY KEY,
    node_id VARCHAR(128) NOT NULL UNIQUE,
    node_type VARCHAR(32) NOT NULL,
    host VARCHAR(128),
    port INT,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    weight INT NOT NULL DEFAULT 1,
    last_heartbeat_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_node_registry_type ON node_registry(node_type);
CREATE INDEX IF NOT EXISTS idx_node_registry_heartbeat ON node_registry(last_heartbeat_at);
