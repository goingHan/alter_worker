-- V3: multiple match conditions for alert rules (JSON array of {key, value})
ALTER TABLE alert_rule ADD COLUMN IF NOT EXISTS match_conditions TEXT;
