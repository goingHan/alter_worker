-- V4: channel-level default template binding
ALTER TABLE message_channel ADD COLUMN IF NOT EXISTS default_template_id BIGINT;
