-- V14 (MySQL): HTML report templates + hosted-file location on alert_message + built-in HTML daily-report template.

-- (a) message_template.format: TEXT (rendered inline, legacy) or HTML (rendered to a file uploaded to object storage).
ALTER TABLE message_template ADD COLUMN format VARCHAR(16) NOT NULL DEFAULT 'TEXT';

-- (b) alert_message records where a hosted HTML report file lives (public URL + object-storage key).
ALTER TABLE alert_message ADD COLUMN content_url VARCHAR(512);
ALTER TABLE alert_message ADD COLUMN object_key VARCHAR(512);

-- (c) built-in self-contained HTML daily-report template (channel-agnostic, read-only in the UI).
INSERT INTO message_template (name, channel_type, content, is_default, builtin, deleted, format)
SELECT '内置-HTML日报模板', NULL,
'<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>告警日报 {{ date }}</title>
<style>
body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1f2d3d;margin:0;padding:24px;background:#f5f7fa;}
.card{max-width:820px;margin:0 auto;background:#fff;border-radius:12px;padding:24px 28px;box-shadow:0 2px 8px rgba(0,0,0,.06);}
h1{font-size:20px;margin:0 0 4px;}
.muted{color:#8492a6;font-size:13px;margin-bottom:16px;}
.total{font-size:26px;font-weight:700;color:#2d8cf0;}
h2{font-size:15px;margin:22px 0 8px;border-left:3px solid #2d8cf0;padding-left:8px;}
table{width:100%;border-collapse:collapse;font-size:13px;}
th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #eef1f6;word-break:break-word;}
th{color:#8492a6;font-weight:500;}
.count{font-weight:600;}
.badge{display:inline-block;padding:1px 8px;border-radius:10px;font-size:12px;background:#eef1f6;color:#515a6e;}
</style>
</head>
<body>
<div class="card">
<h1>告警日报 {{ date }}</h1>
<div class="muted">共 <span class="total">{{ totalCount }}</span> 条告警</div>
<h2>按规则</h2>
<table><thead><tr><th>规则</th><th>数量</th></tr></thead><tbody>{% for r in byRule %}<tr><td>{{ r.ruleName }}</td><td class="count">{{ r.count }}</td></tr>{% endfor %}</tbody></table>
<h2>按级别</h2>
<table><thead><tr><th>级别</th><th>数量</th></tr></thead><tbody>{% for s in bySeverity %}<tr><td>{{ s.severity }}</td><td class="count">{{ s.count }}</td></tr>{% endfor %}</tbody></table>
<h2>按服务</h2>
<table><thead><tr><th>服务</th><th>数量</th></tr></thead><tbody>{% for s in byServiceType %}<tr><td>{{ s.serviceType }}</td><td class="count">{{ s.count }}</td></tr>{% endfor %}</tbody></table>
<h2>告警明细</h2>
<table><thead><tr><th>级别</th><th>规则</th><th>摘要</th><th>时间</th></tr></thead><tbody>{% for a in alerts %}<tr><td><span class="badge">{{ a.severity }}</span></td><td>{{ a.ruleName }}</td><td>{{ a.summary }}</td><td>{{ a.createdAt }}</td></tr>{% endfor %}</tbody></table>
</div>
</body>
</html>',
       0, 1, 0, 'HTML'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE builtin = 1 AND format = 'HTML' AND deleted = 0
);
