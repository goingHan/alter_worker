-- V5 (MySQL): rule->assignee direct binding + assignee email
ALTER TABLE assignee ADD COLUMN email VARCHAR(128);

CREATE TABLE IF NOT EXISTS rule_assignee (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_assignee (rule_id, assignee_id, channel_type),
    KEY idx_rule_assignee_rule_id (rule_id),
    KEY idx_rule_assignee_assignee_id (assignee_id),
    CONSTRAINT fk_rule_assignee_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT fk_rule_assignee_assignee FOREIGN KEY (assignee_id) REFERENCES assignee (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
