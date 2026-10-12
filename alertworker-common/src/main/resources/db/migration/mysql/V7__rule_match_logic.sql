-- V7 (MySQL): match logic (AND/OR) for rule match conditions; per-condition negation lives in match_conditions JSON
ALTER TABLE alert_rule ADD COLUMN match_logic VARCHAR(8) NOT NULL DEFAULT 'OR';
