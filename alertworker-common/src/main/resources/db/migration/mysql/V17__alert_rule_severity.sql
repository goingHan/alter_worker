-- V17 (MySQL): alert rule severity.
--
-- The rule now owns the alert level (INFO / WARNING / CRITICAL) instead of relying on the level carried by the
-- incoming alert system. Existing rules default to WARNING.
ALTER TABLE alert_rule ADD COLUMN severity VARCHAR(16) NOT NULL DEFAULT 'WARNING';
