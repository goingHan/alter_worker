-- V22 (MySQL): per-rule "ignore aggregation" switch.
--
-- When 1, alerts matched by the rule are delivered immediately even when their severity is WARNING or INFO,
-- bypassing the warning digest. Default 0.
ALTER TABLE alert_rule ADD COLUMN ignore_digest TINYINT(1) NOT NULL DEFAULT 0;
