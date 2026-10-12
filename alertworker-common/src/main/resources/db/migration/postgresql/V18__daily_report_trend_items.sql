-- V18: per-rule trend monitoring items for daily reports.
--
-- The report now selects one or more alert RULES as trend monitoring items (a Transfer on the report page); for each
-- selected rule the report computes 平均/中位数/P20/P40/P60/P80/最大/最小 of that rule's per-hour alert counts over the
-- run day (00:00-23:59, Asia/Shanghai). This replaces the previous single trend toggle + configurable percentile.

-- (a) selected trend monitoring rules (mirrors daily_report_rule).
CREATE TABLE IF NOT EXISTS daily_report_trend_rule (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_report(id) ON DELETE CASCADE,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_report_trend_rule UNIQUE (report_id, rule_id)
);

-- (b) the trend toggle and configurable percentile are gone (the trend is now driven by the selected rules, and the
--     percentile set is fixed at P20/P40/P60/P80).
ALTER TABLE daily_report DROP COLUMN IF EXISTS trend_enabled;
ALTER TABLE daily_report DROP COLUMN IF EXISTS trend_percentile;

-- (c) refresh the built-in HTML daily-report template: the single trend block becomes a per-rule table with the fixed
--     percentile set. Guard only upgrades the previous seed (still has 'trend.' and not 'trends').
UPDATE message_template
SET content = '<!DOCTYPE html>
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
{% if trends %}
<h2>按规则趋势（当日）</h2>
<table><thead><tr><th>规则</th><th>平均</th><th>中位数</th><th>P20</th><th>P40</th><th>P60</th><th>P80</th><th>最大</th><th>最小</th></tr></thead><tbody>{% for t in trends %}<tr><td>{{ t.ruleName }}</td><td class="count">{{ t.avg }}</td><td class="count">{{ t.median }}</td><td class="count">{{ t.p20 }}</td><td class="count">{{ t.p40 }}</td><td class="count">{{ t.p60 }}</td><td class="count">{{ t.p80 }}</td><td class="count">{{ t.max }}</td><td class="count">{{ t.min }}</td></tr>{% endfor %}</tbody></table>
<div class="muted">按小时统计每条规则当日 0–23 点的告警数分布</div>
{% endif %}
<h2>按级别</h2>
<table><thead><tr><th>级别</th><th>数量</th></tr></thead><tbody>{% for s in bySeverity %}<tr><td>{{ s.severity }}</td><td class="count">{{ s.count }}</td></tr>{% endfor %}</tbody></table>
<h2>按服务</h2>
<table><thead><tr><th>服务</th><th>数量</th></tr></thead><tbody>{% for s in byServiceType %}<tr><td>{{ s.serviceType }}</td><td class="count">{{ s.count }}</td></tr>{% endfor %}</tbody></table>
<h2>告警明细</h2>
<table><thead><tr><th>级别</th><th>规则</th><th>摘要</th><th>时间</th></tr></thead><tbody>{% for a in alerts %}<tr><td><span class="badge">{{ a.severity }}</span></td><td>{{ a.ruleName }}</td><td>{{ a.summary }}</td><td>{{ a.createdAt }}</td></tr>{% endfor %}</tbody></table>
</div>
</body>
</html>'
WHERE builtin = TRUE AND format = 'HTML' AND deleted = 0
  AND content LIKE '%trend.%' AND content NOT LIKE '%trends%';

-- (d) refresh the built-in TEXT daily-report template (channel-agnostic report fallback).
UPDATE message_template
SET content = E'## 告警日报 {{ date }}\n\n共 {{ totalCount }} 条告警。\n\n### 按规则\n{% for r in byRule %}- {{ r.ruleName }}：{{ r.count }} 条\n{% endfor %}{% if trends %}\n### 按规则趋势（当日）\n{% for t in trends %}- {{ t.ruleName }}：平均 {{ t.avg }}，中位 {{ t.median }}，P20 {{ t.p20 }}，P40 {{ t.p40 }}，P60 {{ t.p60 }}，P80 {{ t.p80 }}，最大 {{ t.max }}，最小 {{ t.min }}\n{% endfor %}{% endif %}\n### 按级别\n{% for s in bySeverity %}- {{ s.severity }}：{{ s.count }} 条\n{% endfor %}\n### 按服务\n{% for s in byServiceType %}- {{ s.serviceType }}：{{ s.count }} 条\n{% endfor %}\n### 告警明细\n{% for a in alerts %}- [{{ a.severity }}] {{ a.ruleName }}｜{{ a.summary }}\n{% endfor %}'
WHERE builtin = TRUE AND channel_type IS NULL AND deleted = 0
  AND content LIKE '%trend.%' AND content NOT LIKE '%trends%';
