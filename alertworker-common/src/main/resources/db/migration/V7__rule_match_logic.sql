-- V7: match logic (AND/OR) for rule match conditions; per-condition negation lives in match_conditions JSON ("not": true)
ALTER TABLE alert_rule ADD COLUMN IF NOT EXISTS match_logic VARCHAR(8) NOT NULL DEFAULT 'OR';
