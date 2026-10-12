-- V13 (MySQL): built-in daily-report template.
--
-- Channel-agnostic (channel_type IS NULL) so the single template works for both DingDing and Email. Marked
-- builtin = 1 so it is read-only in the template UI. Idempotent guard on the same key as the V11 seeds.
INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted)
SELECT '内置-日报模板', NULL,
       '## 告警日报 {{ date }}\n\n共 {{ totalCount }} 条告警。\n\n### 按规则\n{% for r in byRule %}- {{ r.ruleName }}：{{ r.count }} 条\n{% endfor %}\n### 按级别\n{% for s in bySeverity %}- {{ s.severity }}：{{ s.count }} 条\n{% endfor %}\n### 按服务\n{% for s in byServiceType %}- {{ s.serviceType }}：{{ s.count }} 条\n{% endfor %}\n### 告警明细\n{% for a in alerts %}- [{{ a.severity }}] {{ a.ruleName }}｜{{ a.summary }}\n{% endfor %}',
       0, 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = 1 AND channel_type IS NULL AND deleted = 0
);
