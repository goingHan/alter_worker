-- V25: WARNING digest — make the detail table horizontally scrollable (mobile friendly).
--
-- On phones the 7-column detail table overflows the page. Wrap it in a container with overflow-x:auto and give the
-- table a min-width so columns stay readable and the user can scroll horizontally instead of the page breaking layout.
-- Refreshes the built-in WARNING digest template (V21/V24) in place. Matched by name + builtin so a user template is
-- never affected.
UPDATE message_template
SET content = '<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{{ title }} {{ date }}</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: ''Inter'', -apple-system, BlinkMacSystemFont, ''Segoe UI'', Roboto, Helvetica, Arial, sans-serif;
      background: linear-gradient(145deg, #f3f7f3 0%, #eaf1ec 100%);
      color: #1f2d24;
      padding: 2rem 1.5rem;
      min-height: 100vh;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      line-height: 1.5;
      -webkit-font-smoothing: antialiased;
    }
    .card {
      max-width: 1040px;
      width: 100%;
      background: rgba(255, 255, 255, 0.94);
      border-radius: 26px;
      padding: 2.1rem 2.4rem;
      box-shadow: 0 18px 32px -12px rgba(30, 60, 45, 0.16), 0 0 0 1px rgba(255, 255, 255, 0.7) inset;
    }
    h1 { font-size: 1.7rem; font-weight: 600; color: #17301f; display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.15rem; }
    h1::before { content: "⚠️"; font-size: 1.7rem; }
    .muted {
      color: #6b8073; font-size: 0.88rem; margin-bottom: 1.1rem;
      display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.4rem 0.9rem;
      border-bottom: 1px solid rgba(150, 190, 165, 0.35); padding-bottom: 0.9rem;
    }
    .total {
      font-size: 2rem; font-weight: 700; letter-spacing: -0.02em; line-height: 1.2; margin-right: 0.2rem;
      background: linear-gradient(135deg, #2f8f5b, #57b97e);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
    }
    h2 {
      font-size: 1.12rem; font-weight: 600; color: #26492f; margin: 1.7rem 0 0.7rem;
      padding-left: 0.7rem; border-left: 5px solid #57b97e; display: flex; align-items: center;
    }
    h2:first-of-type { margin-top: 0.4rem; }
    table {
      width: 100%; border-collapse: separate; border-spacing: 0; font-size: 0.87rem;
      background: #ffffffd8; border-radius: 14px; overflow: hidden;
      box-shadow: 0 2px 6px rgba(20, 50, 35, 0.04), 0 0 0 1px #e2efe6 inset; margin-bottom: 0.4rem;
    }
    th, td { text-align: left; padding: 0.62rem 0.9rem; border-bottom: 1px solid #eef4f0; word-break: break-word; }
    th {
      background: #f4faf6; color: #4a6b56; font-weight: 600; font-size: 0.78rem;
      text-transform: uppercase; letter-spacing: 0.03em; border-bottom: 1.5px solid #d7e8dd; white-space: nowrap;
    }
    tr:last-child td { border-bottom: none; }
    tr:hover td { background: #f5fbf7; }
    .count { font-weight: 600; color: #1d6b44; font-variant-numeric: tabular-nums; text-align: right; }
    .badge {
      display: inline-block; padding: 0.15rem 0.65rem; border-radius: 30px; font-size: 0.74rem;
      font-weight: 600; background: #eef1f6; color: #515a6e; border: 1px solid #d8dee9; white-space: nowrap;
    }
    .badge[data-severity="critical"], .badge[data-severity="严重"], .badge[data-severity="高"] {
      background: #ffebe9; border-color: #ffb3ab; color: #b13a2b;
    }
    .badge[data-severity="warning"], .badge[data-severity="中"] {
      background: #fff4e0; border-color: #f7d8a0; color: #9b6b1c;
    }
    .badge[data-severity="info"], .badge[data-severity="低"] {
      background: #e8f2fe; border-color: #b8d4f0; color: #1e6b9e;
    }
    .search-bar { display: flex; align-items: center; gap: 0.6rem; margin: 0.6rem 0 0.9rem; }
    .search-bar input {
      flex: 1; max-width: 420px; padding: 0.5rem 0.85rem; font-size: 0.9rem; color: #1f2d24;
      border: 1px solid #cfe3d6; border-radius: 10px; outline: none; background: #fff; transition: border-color 0.15s, box-shadow 0.15s;
    }
    .search-bar input:focus { border-color: #57b97e; box-shadow: 0 0 0 3px rgba(87, 185, 126, 0.15); }
    .search-hint { font-size: 0.78rem; color: #8aa193; }
    .empty { text-align: center; color: #98aec7; font-style: italic; padding: 1.4rem; }
    .hidden-row { display: none !important; }
    /* #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: detail table scrolls horizontally on narrow screens */
    .table-scroll { width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .detail-table { min-width: 720px; }
    .detail-table td:nth-child(3) { color: #2d4059; }
    .detail-table td:nth-child(4) { color: #2d4059; }
    .detail-table td:nth-child(5) { white-space: nowrap; font-size: 0.82rem; color: #2d4059; }
    .detail-table td:nth-child(7) { white-space: nowrap; font-size: 0.78rem; color: #6d7f94; }
    @media (max-width: 700px) {
      body { padding: 1rem; }
      .card { padding: 1.4rem 1.1rem; border-radius: 20px; }
      h1 { font-size: 1.4rem; }
      .total { font-size: 1.7rem; }
      th, td { padding: 0.5rem 0.6rem; font-size: 0.8rem; }
      .detail-table td:nth-child(5) { white-space: normal; }
      .detail-table td:nth-child(7) { white-space: normal; }
    }
  </style>
</head>
<body>
<div class="card">
  <h1>{{ title }} {{ date }}</h1>
  <div class="muted">
    <span>共 <span class="total">{{ totalCount }}</span> 条告警</span>
    <span>生成时间：{{ generatedAt }}</span>
  </div>

  <h2>按规则</h2>
  <table>
    <thead><tr><th>规则</th><th>数量</th></tr></thead>
    <tbody>
      {% for r in byRule %}
      <tr><td>{{ r.ruleName }}</td><td class="count">{{ r.count }}</td></tr>
      {% endfor %}
    </tbody>
  </table>

  <h2>按级别</h2>
  <table>
    <thead><tr><th>级别</th><th>数量</th></tr></thead>
    <tbody>
      {% for s in bySeverity %}
      <tr><td><span class="badge" data-severity="{{ s.severity }}">{{ s.severity }}</span></td><td class="count">{{ s.count }}</td></tr>
      {% endfor %}
    </tbody>
  </table>

  <h2>按服务</h2>
  <table>
    <thead><tr><th>服务</th><th>数量</th></tr></thead>
    <tbody>
      {% for s in byServiceType %}
      <tr><td>{{ s.serviceType }}</td><td class="count">{{ s.count }}</td></tr>
      {% endfor %}
    </tbody>
  </table>

  <!-- 按责任人：每个责任人会收到多少条告警 -->
  <h2>按责任人</h2>
  <table>
    <thead><tr><th>责任人</th><th>数量</th></tr></thead>
    <tbody>
      {% for p in byAssignee %}
      <tr><td>{{ p.assignee }}</td><td class="count">{{ p.count }}</td></tr>
      {% endfor %}
    </tbody>
  </table>

  <h2>告警明细</h2>
  <div class="search-bar">
    <input id="aw-kw" type="search" placeholder="输入关键字筛选（规则 / 摘要 / 内容 / 责任人 / 服务）" oninput="awFilter(this.value)">
    <span class="search-hint" id="aw-kw-count"></span>
  </div>
  <div class="table-scroll">
  <table class="detail-table" id="aw-alerts">
    <thead>
      <tr><th>级别</th><th>规则</th><th>摘要</th><th>内容</th><th>责任人</th><th>服务</th><th>时间</th></tr>
    </thead>
    <tbody>
      {% for a in alerts %}
      <tr data-search="{{ a.ruleName }} {{ a.summary }} {{ a.content }} {{ a.assignees }} {{ a.serviceType }}">
        <td><span class="badge" data-severity="{{ a.severity }}">{{ a.severity }}</span></td>
        <td>{{ a.ruleName }}</td>
        <td>{{ a.summary }}</td>
        <td>{{ a.content }}</td>
        <td>{{ a.assignees }}</td>
        <td>{{ a.serviceType }}</td>
        <td>{{ a.createdAt }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>
  </div>
</div>
<script>
  var awRows = [];
  (function () {
    var body = document.querySelector(''#aw-alerts tbody'');
    if (body) { awRows = Array.prototype.slice.call(body.querySelectorAll(''tr'')); }
    var counter = document.getElementById(''aw-kw-count'');
    if (counter) { counter.textContent = ''共 '' + awRows.length + '' 条''; }
  })();
  function awFilter(kw) {
    var q = (kw || '''').trim().toLowerCase();
    var shown = 0;
    awRows.forEach(function (row) {
      var text = (row.getAttribute(''data-search'') || '''').toLowerCase();
      var hit = q === '''' || text.indexOf(q) !== -1;
      row.classList.toggle(''hidden-row'', !hit);
      if (hit) { shown++; }
    });
    var counter = document.getElementById(''aw-kw-count'');
    if (counter) { counter.textContent = shown + '' / '' + awRows.length + '' 条''; }
  }
</script>
</body>
</html>',
    format = 'HTML'
WHERE builtin = TRUE AND channel_type IS NULL AND name = '内置-WARNING汇总模板' AND deleted = 0;
