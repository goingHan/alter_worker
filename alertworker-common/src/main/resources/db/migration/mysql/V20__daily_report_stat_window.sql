-- V20 (MySQL): daily-report configurable statistics window + remove the alert-detail section from built-in templates.

-- (a) daily_report.stat_start_time / stat_end_time: aggregation window within the run day (Asia/Shanghai), minute
--     granularity, inclusive at both ends. NULL falls back to the full day (00:00-24:00).
ALTER TABLE daily_report
    ADD COLUMN stat_start_time TIME NULL,
    ADD COLUMN stat_end_time TIME NULL;

-- (b) remove the "告警明细" section from the built-in HTML daily-report template (runs before the V21 insert).
UPDATE message_template
SET content = '<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>告警日报 {{ date }}</title>
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    body {
      font-family: ''Inter'', -apple-system, BlinkMacSystemFont, ''Segoe UI'', Roboto, Helvetica, Arial, sans-serif;
      background: linear-gradient(145deg, #f0f4fa 0%, #e9eef6 100%);
      color: #1a2634;
      padding: 2rem 1.5rem;
      min-height: 100vh;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      line-height: 1.5;
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
    }

    .card {
      max-width: 1000px;
      width: 100%;
      background: rgba(255, 255, 255, 0.92);
      backdrop-filter: blur(4px);
      border-radius: 28px;
      padding: 2.25rem 2.5rem;
      box-shadow: 0 20px 35px -10px rgba(0, 20, 30, 0.15),
                  0 0 0 1px rgba(255, 255, 255, 0.7) inset,
                  0 4px 12px rgba(0, 0, 0, 0.02);
      transition: box-shadow 0.2s ease;
    }

    .card:hover {
      box-shadow: 0 25px 40px -12px rgba(0, 30, 60, 0.2),
                  0 0 0 1px rgba(255, 255, 255, 0.8) inset,
                  0 6px 14px rgba(0, 0, 0, 0.02);
    }

    h1 {
      font-size: 1.85rem;
      font-weight: 600;
      letter-spacing: -0.02em;
      margin: 0 0 0.2rem;
      color: #0e1e2f;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    h1::before {
      content: "📋";
      font-size: 1.9rem;
      filter: drop-shadow(0 2px 4px rgba(45, 140, 240, 0.25));
      font-weight: 400;
    }

    .muted {
      color: #5f738c;
      font-size: 0.9rem;
      margin-bottom: 1.25rem;
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.4rem 0.8rem;
      border-bottom: 1px solid rgba(175, 195, 220, 0.3);
      padding-bottom: 1rem;
    }

    .total {
      font-size: 2.2rem;
      font-weight: 700;
      color: #1e6bc7;
      background: linear-gradient(135deg, #1a5eb8, #2d8cf0);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin-right: 0.2rem;
      letter-spacing: -0.02em;
      line-height: 1.2;
    }

    h2 {
      font-size: 1.2rem;
      font-weight: 600;
      letter-spacing: -0.01em;
      color: #1c3e5c;
      margin: 2rem 0 0.85rem;
      padding-left: 0.75rem;
      border-left: 5px solid #2d8cf0;
      line-height: 1.3;
      display: flex;
      align-items: center;
    }

    h2:first-of-type {
      margin-top: 0.5rem;
    }

    table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      font-size: 0.88rem;
      background: #ffffffd8;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 2px 6px rgba(0, 20, 40, 0.03), 0 0 0 1px #e2eaf2 inset;
      margin-bottom: 0.5rem;
      transition: box-shadow 0.15s;
    }

    table:hover {
      box-shadow: 0 6px 14px rgba(0, 30, 60, 0.06), 0 0 0 1px #d0deed inset;
    }

    th, td {
      text-align: left;
      padding: 0.7rem 1rem;
      border-bottom: 1px solid #eef3f9;
      word-break: break-word;
    }

    th {
      background: #f6faff;
      color: #3f5a7a;
      font-weight: 600;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      border-bottom: 1.5px solid #dae5f0;
      white-space: nowrap;
    }

    tr:last-child td {
      border-bottom: none;
    }

    td {
      color: #1e2e42;
      font-weight: 400;
      transition: background 0.1s;
    }

    tr:hover td {
      background: #f4f9ff;
    }

    .count {
      font-weight: 600;
      color: #1b4c7c;
      font-variant-numeric: tabular-nums;
    }

    /* 数字右对齐更具报表感 */
    td.count, th:not(:first-child):not(:nth-child(2)) {
      text-align: right;
    }

    /* 规则趋势表格微调 */
    .trend-table td:first-child,
    .trend-table th:first-child {
      font-weight: 500;
      color: #1f3a5c;
    }

    .trend-table td:not(:first-child) {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }

    .badge {
      display: inline-block;
      padding: 0.2rem 0.7rem;
      border-radius: 30px;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.01em;
      background: #e9eff8;
      color: #2e4a6b;
      border: 1px solid #cbd8ea;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
      transition: all 0.1s;
      white-space: nowrap;
    }

    /* 给严重级别一点色彩暗示（通过属性选择器近似匹配） */
    .badge[data-severity="critical"],
    .badge[data-severity="严重"],
    .badge[data-severity="高"] {
      background: #ffebe9;
      border-color: #ffb3ab;
      color: #b13a2b;
    }

    .badge[data-severity="warning"],
    .badge[data-severity="中"] {
      background: #fff4e0;
      border-color: #f7d8a0;
      color: #9b6b1c;
    }

    .badge[data-severity="info"],
    .badge[data-severity="低"] {
      background: #e8f2fe;
      border-color: #b8d4f0;
      color: #1e6b9e;
    }

    /* 空状态占位（表格中若无数据） */
    tbody:empty::after {
      content: "暂无数据";
      display: table-row;
      text-align: center;
      color: #98aec7;
      font-style: italic;
      padding: 1.5rem;
    }

    /* 让响应式也优雅 */
    @media (max-width: 700px) {
      body {
        padding: 1rem;
      }
      .card {
        padding: 1.5rem 1.2rem;
        border-radius: 22px;
      }
      h1 {
        font-size: 1.5rem;
      }
      .total {
        font-size: 1.9rem;
      }
      th, td {
        padding: 0.6rem 0.7rem;
        font-size: 0.8rem;
      }
      h2 {
        font-size: 1.05rem;
      }
      table {
        border-radius: 12px;
      }
    }

    @media (max-width: 480px) {
      .card {
        padding: 1.2rem 0.9rem;
      }
      h1 {
        font-size: 1.3rem;
      }
      .muted {
        font-size: 0.8rem;
      }
      .total {
        font-size: 1.6rem;
      }
      th, td {
        padding: 0.45rem 0.5rem;
        font-size: 0.74rem;
      }
      .badge {
        font-size: 0.68rem;
        padding: 0.15rem 0.5rem;
      }
    }

    /* 自定义滚动条 */
    ::-webkit-scrollbar {
      width: 8px;
      height: 8px;
    }
    ::-webkit-scrollbar-track {
      background: #eef3f9;
      border-radius: 10px;
    }
    ::-webkit-scrollbar-thumb {
      background: #b7c8dd;
      border-radius: 10px;
    }
    ::-webkit-scrollbar-thumb:hover {
      background: #8fa5c0;
    }
  </style>
</head>
<body>
<div class="card">
  <h1>告警日报 {{ date }}</h1>
  <div class="muted">
    <span>共 <span class="total">{{ totalCount }}</span> 条告警</span>
  </div>

  <!-- 按规则 -->
  <h2>按规则</h2>
  <table>
    <thead>
      <tr>
        <th>规则</th>
        <th>数量</th>
      </tr>
    </thead>
    <tbody>
      {% for r in byRule %}
      <tr>
        <td>{{ r.ruleName }}</td>
        <td class="count">{{ r.count }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>

  <!-- 按规则趋势（当日） -->
  {% if trends %}
  <h2>按规则趋势（当日）</h2>
  <table class="trend-table">
    <thead>
      <tr>
        <th>规则</th>
        <th>平均</th>
        <th>中位数</th>
        <th>P20</th>
        <th>P40</th>
        <th>P60</th>
        <th>P80</th>
        <th>最大</th>
        <th>最小</th>
      </tr>
    </thead>
    <tbody>
      {% for t in trends %}
      <tr>
        <td>{{ t.ruleName }}</td>
        <td class="count">{{ t.avg }}</td>
        <td class="count">{{ t.median }}</td>
        <td class="count">{{ t.p20 }}</td>
        <td class="count">{{ t.p40 }}</td>
        <td class="count">{{ t.p60 }}</td>
        <td class="count">{{ t.p80 }}</td>
        <td class="count">{{ t.max }}</td>
        <td class="count">{{ t.min }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>
  <div class="muted" style="margin-top: 0.25rem; border-bottom: none; padding-bottom: 0.25rem;">
    ⏱️ 按小时统计每条规则当日 0–23 点的告警数分布
  </div>
  {% endif %}

  <!-- 按级别 -->
  <h2>按级别</h2>
  <table>
    <thead>
      <tr>
        <th>级别</th>
        <th>数量</th>
      </tr>
    </thead>
    <tbody>
      {% for s in bySeverity %}
      <tr>
        <td><span class="badge" data-severity="{{ s.severity }}">{{ s.severity }}</span></td>
        <td class="count">{{ s.count }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>

  <!-- 按服务 -->
  <h2>按服务</h2>
  <table>
    <thead>
      <tr>
        <th>服务</th>
        <th>数量</th>
      </tr>
    </thead>
    <tbody>
      {% for s in byServiceType %}
      <tr>
        <td>{{ s.serviceType }}</td>
        <td class="count">{{ s.count }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>
</div>
</body>
</html>'
WHERE builtin = 1 AND format = 'HTML' AND deleted = 0;

-- (c) remove the alert-detail section from the built-in TEXT daily-report template.
UPDATE message_template
SET content = '## 告警日报 {{ date }}\n\n共 {{ totalCount }} 条告警。\n\n### 按规则\n{% for r in byRule %}- {{ r.ruleName }}：{{ r.count }} 条\n{% endfor %}{% if trends %}\n### 按规则趋势（当日）\n{% for t in trends %}- {{ t.ruleName }}：平均 {{ t.avg }}，中位 {{ t.median }}，P20 {{ t.p20 }}，P40 {{ t.p40 }}，P60 {{ t.p60 }}，P80 {{ t.p80 }}，最大 {{ t.max }}，最小 {{ t.min }}\n{% endfor %}{% endif %}\n### 按级别\n{% for s in bySeverity %}- {{ s.severity }}：{{ s.count }} 条\n{% endfor %}\n### 按服务\n{% for s in byServiceType %}- {{ s.serviceType }}：{{ s.count }} 条\n{% endfor %}'
WHERE builtin = 1 AND channel_type IS NULL AND deleted = 0;
