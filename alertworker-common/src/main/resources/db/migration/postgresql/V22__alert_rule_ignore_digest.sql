-- V22: per-rule "ignore aggregation" switch.
--
-- When TRUE, alerts matched by the rule are delivered immediately even when their severity is WARNING or INFO,
-- bypassing the warning digest. Default FALSE (normal digest behaviour for WARNING/INFO).
ALTER TABLE alert_rule ADD COLUMN IF NOT EXISTS ignore_digest BOOLEAN NOT NULL DEFAULT FALSE;
