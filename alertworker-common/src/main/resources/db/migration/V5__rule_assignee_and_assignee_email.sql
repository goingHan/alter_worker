-- V5: rule->assignee direct binding + assignee email
ALTER TABLE assignee ADD COLUMN IF NOT EXISTS email VARCHAR(128);

CREATE TABLE IF NOT EXISTS rule_assignee (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    assignee_id BIGINT NOT NULL REFERENCES assignee(id) ON DELETE CASCADE,
    channel_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rule_assignee UNIQUE (rule_id, assignee_id, channel_type)
);

CREATE INDEX idx_rule_assignee_rule_id ON rule_assignee(rule_id);
CREATE INDEX idx_rule_assignee_assignee_id ON rule_assignee(assignee_id);
