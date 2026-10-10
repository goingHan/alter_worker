-- V12: One-time UTC -> Asia/Shanghai (CST) correction of existing timestamps.
--
-- The whole system previously ran with the JVM default timezone = UTC, so every timestamp
-- written by Java (createdAt/updatedAt via MyMetaObjectHandler, status-transition timestamps)
-- holds a UTC wall-clock value. As of this release the backend pins the JVM + Jackson timezone
-- to Asia/Shanghai (TimezoneConfig), so all NEW rows will be written in CST.
-- To keep the wall-clock consistent between old and new rows, this migration shifts every
-- existing timestamp column forward by 8 hours (UTC -> CST). Flyway runs this exactly once.
--
-- EXCLUDED by design:
--   * scheduled_job.next_run_at -- it is computed directly in Asia/Shanghai by the scheduler
--     (it was never UTC wall-clock), so it must NOT be shifted.
--
-- NULL values stay NULL: `NULL + interval '8 hours'` is NULL in PostgreSQL; the explicit
-- `WHERE <col> IS NOT NULL` guards make the intent clear regardless.

-- ---------- V1: init schema ----------
UPDATE api_key SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE api_key SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE message_channel SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE message_channel SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE assignee SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE assignee SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE communication_group SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE communication_group SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE group_assignee SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE group_assignee SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE message_template SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE message_template SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE alert_rule SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE alert_rule SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE rule_group SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE alert_rule_template SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE alert_message SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE alert_message SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE dead_letter_message SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE audit_log SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE dictionary SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE dictionary SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

-- ---------- V2: message trace + node registry ----------
UPDATE alert_message SET received_at = received_at + interval '8 hours' WHERE received_at IS NOT NULL;
UPDATE alert_message SET gw_sent_at = gw_sent_at + interval '8 hours' WHERE gw_sent_at IS NOT NULL;
UPDATE alert_message SET worker_received_at = worker_received_at + interval '8 hours' WHERE worker_received_at IS NOT NULL;
UPDATE alert_message SET worker_sent_at = worker_sent_at + interval '8 hours' WHERE worker_sent_at IS NOT NULL;
UPDATE alert_message SET finished_at = finished_at + interval '8 hours' WHERE finished_at IS NOT NULL;

UPDATE node_registry SET last_heartbeat_at = last_heartbeat_at + interval '8 hours' WHERE last_heartbeat_at IS NOT NULL;
UPDATE node_registry SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE node_registry SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

-- ---------- V5: rule -> assignee ----------
UPDATE rule_assignee SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

-- ---------- V8: rule -> channel binding ----------
UPDATE alert_rule_channel SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

-- ---------- V11: scheduled jobs + daily report ----------
-- NOTE: scheduled_job.next_run_at is intentionally NOT shifted (already CST).
UPDATE scheduled_job SET last_run_at = last_run_at + interval '8 hours' WHERE last_run_at IS NOT NULL;
UPDATE scheduled_job SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE scheduled_job SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE daily_report SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
UPDATE daily_report SET updated_at = updated_at + interval '8 hours' WHERE updated_at IS NOT NULL;

UPDATE daily_report_rule SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE daily_report_recipient SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;

UPDATE daily_report_channel SET created_at = created_at + interval '8 hours' WHERE created_at IS NOT NULL;
