-- V15 (MySQL): assignee enable/disable flag.
--
-- A disabled assignee stays configured (group/rule bindings untouched) but is skipped during recipient resolution.
ALTER TABLE assignee ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;
