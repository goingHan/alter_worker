-- V12 (MySQL): One-time UTC -> Asia/Shanghai (CST) correction of existing timestamps (equivalent to the
-- PostgreSQL migration, using DATE_ADD instead of interval arithmetic). No-op on a fresh database.
--
-- EXCLUDED by design: scheduled_job.next_run_at is already computed in Asia/Shanghai and must NOT be shifted.

-- ---------- V1: init schema ----------
UPDATE api_key SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE api_key SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE message_channel SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE message_channel SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE assignee SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE assignee SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE communication_group SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE communication_group SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE group_assignee SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE group_assignee SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE message_template SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE message_template SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE alert_rule SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE alert_rule SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE rule_group SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE alert_rule_template SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE alert_message SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE alert_message SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE dead_letter_message SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE audit_log SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE dictionary SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE dictionary SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

-- ---------- V2: message trace + node registry ----------
UPDATE alert_message SET received_at = DATE_ADD(received_at, INTERVAL 8 HOUR) WHERE received_at IS NOT NULL;
UPDATE alert_message SET gw_sent_at = DATE_ADD(gw_sent_at, INTERVAL 8 HOUR) WHERE gw_sent_at IS NOT NULL;
UPDATE alert_message SET worker_received_at = DATE_ADD(worker_received_at, INTERVAL 8 HOUR) WHERE worker_received_at IS NOT NULL;
UPDATE alert_message SET worker_sent_at = DATE_ADD(worker_sent_at, INTERVAL 8 HOUR) WHERE worker_sent_at IS NOT NULL;
UPDATE alert_message SET finished_at = DATE_ADD(finished_at, INTERVAL 8 HOUR) WHERE finished_at IS NOT NULL;

UPDATE node_registry SET last_heartbeat_at = DATE_ADD(last_heartbeat_at, INTERVAL 8 HOUR) WHERE last_heartbeat_at IS NOT NULL;
UPDATE node_registry SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE node_registry SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

-- ---------- V5: rule -> assignee ----------
UPDATE rule_assignee SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

-- ---------- V8: rule -> channel binding ----------
UPDATE alert_rule_channel SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

-- ---------- V11: scheduled jobs + daily report ----------
-- NOTE: scheduled_job.next_run_at is intentionally NOT shifted (already CST).
UPDATE scheduled_job SET last_run_at = DATE_ADD(last_run_at, INTERVAL 8 HOUR) WHERE last_run_at IS NOT NULL;
UPDATE scheduled_job SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE scheduled_job SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE daily_report SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
UPDATE daily_report SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR) WHERE updated_at IS NOT NULL;

UPDATE daily_report_rule SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE daily_report_recipient SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;

UPDATE daily_report_channel SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR) WHERE created_at IS NOT NULL;
