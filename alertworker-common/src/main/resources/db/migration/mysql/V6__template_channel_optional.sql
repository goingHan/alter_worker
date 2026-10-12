-- V6 (MySQL): templates are channel-agnostic; the channel is decided by rule binding (alert_rule_template.channel_type)
ALTER TABLE message_template MODIFY COLUMN channel_type VARCHAR(32) NULL;
