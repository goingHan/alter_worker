-- V4 (MySQL): channel-level default template binding
ALTER TABLE message_channel ADD COLUMN default_template_id BIGINT;
