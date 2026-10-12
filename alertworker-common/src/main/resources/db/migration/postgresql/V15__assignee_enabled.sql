-- V15: assignee enable/disable flag.
--
-- A disabled assignee stays configured (group/rule bindings untouched) but is skipped during recipient resolution, so it
-- receives no email and is not @-mentioned in DingDing. Existing rows default to enabled.
ALTER TABLE assignee ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
