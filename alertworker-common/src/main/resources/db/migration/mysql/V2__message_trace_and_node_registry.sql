-- V2 (MySQL): message traceability (unique message_no, raw payload, timing columns) + node registry
ALTER TABLE alert_message
    ADD COLUMN message_no VARCHAR(64),
    ADD COLUMN raw_payload LONGTEXT,
    ADD COLUMN received_at DATETIME,
    ADD COLUMN gw_sent_at DATETIME,
    ADD COLUMN worker_received_at DATETIME,
    ADD COLUMN worker_sent_at DATETIME,
    ADD COLUMN finished_at DATETIME;

CREATE UNIQUE INDEX uk_alert_message_no ON alert_message (message_no);
CREATE INDEX idx_alert_message_received_at ON alert_message (received_at);

UPDATE alert_message SET message_no = CONCAT('AW-', id) WHERE message_no IS NULL;
UPDATE alert_message SET received_at = created_at WHERE received_at IS NULL;

CREATE TABLE IF NOT EXISTS node_registry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    node_id VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    host VARCHAR(128),
    port INT,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    weight INT NOT NULL DEFAULT 1,
    last_heartbeat_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_registry_node_id (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_node_registry_type ON node_registry (node_type);
CREATE INDEX idx_node_registry_heartbeat ON node_registry (last_heartbeat_at);
