-- V3 (MySQL): multiple match conditions for alert rules (JSON array of {key, value})
ALTER TABLE alert_rule ADD COLUMN match_conditions LONGTEXT;
