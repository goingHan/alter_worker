-- V6: templates are channel-agnostic; the channel is decided by rule binding (alert_rule_template.channel_type)
ALTER TABLE message_template ALTER COLUMN channel_type DROP NOT NULL;
