-- V16 (MySQL): per-rule trend statistics for daily reports + built-in report template refresh.

-- (a) daily_report trend toggle + configurable percentile.
ALTER TABLE daily_report ADD COLUMN trend_enabled TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE daily_report ADD COLUMN trend_percentile INT NOT NULL DEFAULT 95;

-- (b) refresh the built-in HTML daily-report template with the per-rule trend block.
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
{% if trend %}
<h2>按规则趋势（当日）</h2>
<table><thead><tr><th>指标</th><th>数值</th></tr></thead><tbody><tr><td>平均数</td><td class="count">{{ trend.avg }}</td></tr><tr><td>中位数</td><td class="count">{{ trend.median }}</td></tr><tr><td>P{{ trend.percentile }}</td><td class="count">{{ trend.percentileValue }}</td></tr><tr><td>最大值</td><td class="count">{{ trend.max }}</td></tr><tr><td>最小值</td><td class="count">{{ trend.min }}</td></tr></tbody></table>
<div class="muted">跨 {{ trend.ruleCount }} 条已选规则的当日告警数</div>
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
WHERE builtin = 1 AND format = 'HTML' AND deleted = 0
  AND content LIKE '%按服务%' AND content NOT LIKE '%trend.%';

-- (c) refresh the built-in TEXT daily-report template (channel-agnostic report fallback) with the trend block.
UPDATE message_template
SET content = '## 告警日报 {{ date }}\n\n共 {{ totalCount }} 条告警。\n\n### 按规则\n{% for r in byRule %}- {{ r.ruleName }}：{{ r.count }} 条\n{% endfor %}{% if trend %}\n### 按规则趋势（当日）\n- 规则数：{{ trend.ruleCount }}\n- 平均数：{{ trend.avg }}\n- 中位数：{{ trend.median }}\n- P{{ trend.percentile }}：{{ trend.percentileValue }}\n- 最大值：{{ trend.max }}\n- 最小值：{{ trend.min }}\n{% endif %}\n### 按级别\n{% for s in bySeverity %}- {{ s.severity }}：{{ s.count }} 条\n{% endfor %}\n### 按服务\n{% for s in byServiceType %}- {{ s.serviceType }}：{{ s.count }} 条\n{% endfor %}\n### 告警明细\n{% for a in alerts %}- [{{ a.severity }}] {{ a.ruleName }}｜{{ a.summary }}\n{% endfor %}'
WHERE builtin = 1 AND channel_type IS NULL AND deleted = 0
  AND content LIKE '%按服务%' AND content NOT LIKE '%trend.%';
