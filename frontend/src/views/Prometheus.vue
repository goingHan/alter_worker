// #AI-CODE-FILE-dev_release/v0.0.1-20260901: Prometheus integration guide page
<template>
  <div class="integration">
    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">Prometheus 对接</span>
        <span class="panel-sub">通过 Alertmanager Webhook 将告警推送到 AlertWorker</span>
      </div>
      <p class="intro">
        AlertWorker 以 HTTP 接口接收 Prometheus Alertmanager 转发的告警，按「告警规则」的匹配条件命中后，
        带出规则自己的<strong>级别</strong>、接收人与通知渠道进行投递；<strong>未命中任何规则的告警会被直接丢弃</strong>并计入「未匹配」。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">接入步骤</div>
      <ol class="steps">
        <li>在「接入管理 → API 密钥」创建一个 API 密钥，记下<strong>密钥值</strong>（每个密钥默认 100 条/分钟限流）。</li>
        <li>在「通知配置 → 告警规则」新建规则：告警类型选 <code>Prometheus</code>，填写匹配条件（可加多条，用 且/或/非 组合）。</li>
        <li>按需设置规则的<strong>级别</strong>（INFO/WARNING/CRITICAL）以及「静默 / 仅日报 / 忽略聚合」，并关联通信组、值班人员、通知渠道与消息模板。</li>
        <li>在 Prometheus Alertmanager 配置文件中新增一个 <code>webhook</code> 接收器，指向下面的地址。</li>
        <li>触发一条告警，即可在「告警历史」中看到消息并追踪投递状态；未匹配 / 被静默的记录会单独标注。</li>
      </ol>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">请求地址与鉴权</div>
      <div class="kv">
        <div class="kv-row"><span class="kv-key">接口地址</span><code class="kv-val">POST /pw/alertworker/gw/api/v1/alerts/prometheus</code></div>
        <div class="kv-row"><span class="kv-key">请求头</span><code class="kv-val">X-API-Key: &lt;你的 API 密钥值&gt;</code></div>
        <div class="kv-row"><span class="kv-key">Content-Type</span><code class="kv-val">application/json</code></div>
      </div>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">响应状态码</div>
      <ul class="notes">
        <li><code>200</code> · <code>GW_SENT</code>：命中规则并已入队，<code>recipientCount</code> 为解析到的接收人数。</li>
        <li><code>200</code> · <code>UNMATCHED</code>：没有命中任何规则，已直接丢弃（返回体含 <code>reason</code>）；仅落一条「未匹配」记录。</li>
        <li><code>200</code> · <code>SILENCED</code>：命中的规则处于静默，已直接丢弃。</li>
        <li><code>200</code> · <code>WARNING_PENDING</code>：级别为 WARNING/INFO 且「告警汇总」已开启，已入库等待汇总发送。</li>
        <li><code>401</code>：缺失或无效的 <code>X-API-Key</code>。</li>
        <li><code>429</code>：超过该密钥的限流阈值（默认 100 条/分钟）。</li>
        <li><code>5xx</code>：处理异常，记录为失败并进入死信队列。</li>
      </ul>
      <p class="intro" style="margin-top: 10px;">
        「未匹配」「已静默」都返回 200（告警已受理处理），以免上游按 <code>repeat_interval</code> 无限重发。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">Alertmanager 配置示例</div>
      <pre class="code-block"><code>receivers:
  - name: 'alertworker'
    webhook_configs:
      - url: 'http://172.168.1.249:24980/pw/alertworker/gw/api/v1/alerts/prometheus'
        send_resolved: true
        http_config:
          http_headers:
            X-API-Key:
              values:
                - '&lt;你的 API 密钥值&gt;'</code></pre>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">告警报文示例</div>
      <pre class="code-block"><code>{
  "receiver": "alertworker",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "CPUHigh",
        "severity": "critical",
        "service_type": "core",
        "instance": "web-01"
      },
      "annotations": {
        "summary": "CPU 使用率过高",
        "description": "实例 web-01 CPU 使用率超过 90%"
      },
      "startsAt": "2026-09-01T10:00:00Z"
    }
  ]
}</code></pre>
      <p class="intro" style="margin-top: 10px;">
        报文中的 <code>severity</code> 只是参考值：命中规则后会被规则的<strong>级别</strong>覆盖，模板与日报按规则级别展示。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">匹配规则说明</div>
      <ul class="notes">
        <li><strong>匹配顺序</strong>：规则按创建顺序依次匹配，命中第一条即停止。</li>
        <li><strong>匹配值</strong>：期望的值，如 <code>CPUHigh</code>；<strong>留空表示「该 label 存在即命中」</strong>。</li>
        <li><strong>多条件运算符</strong>：可为每条条件选择 <code>且</code> / <code>或</code> / <code>非</code>，自上而下左结合；<code>非</code> 表示该条件不成立（字段不存在也算成立）。</li>
        <li><strong>取值位置</strong>：Prometheus 取第一个 <code>alerts[0].labels</code> 中的 label；JSON 取报文顶层 key。</li>
        <li><strong>service_type</strong>：Prometheus 取 label <code>service_type</code>，JSON 取顶层 <code>service_type</code>，用于按服务类型过滤通知人员。</li>
        <li><strong>级别</strong>：由规则决定（<code>INFO</code>/<code>WARNING</code>/<code>CRITICAL</code>），覆盖报文里的 <code>severity</code>。</li>
        <li><strong>未匹配</strong>：没有任何规则命中 → 状态「未匹配」，直接丢弃（不发送、不重试、不进死信）。</li>
        <li><strong>静默</strong>：命中的规则被静默 → 状态「已静默」，直接丢弃。</li>
        <li><strong>仅日报</strong>：规则开启后告警入库待日报，由「告警日报」任务按天聚合发送。</li>
        <li><strong>告警汇总</strong>：当「告警汇总」开启且级别为 WARNING/INFO 时，告警入库待汇总、由汇总任务统一推送；如需立即逐条发送，开启该规则的「忽略聚合」。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">JSON 告警接入</div>
      <div class="kv">
        <div class="kv-row"><span class="kv-key">接口地址</span><code class="kv-val">POST /pw/alertworker/gw/api/v1/alerts/json</code></div>
        <div class="kv-row"><span class="kv-key">请求头</span><code class="kv-val">X-API-Key: &lt;你的 API 密钥值&gt;</code></div>
      </div>
      <p class="intro" style="margin-top: 10px;">
        自研系统可直接推送 JSON 报文：规则类型选 <code>JSON</code>，匹配 Key 填报文<strong>顶层字段名</strong>（如 <code>alertname</code>）。
      </p>
      <pre class="code-block"><code>{
  "alertname": "OrderTimeout",
  "service_type": "order",
  "level": "WARNING",
  "title": "订单超时",
  "content": "订单 12345 处理超时 5 分钟"
}</code></pre>
      <p class="intro" style="margin-top: 10px;">
        模板变量直接取自顶层字段（如 <code v-pre>{{ title }}</code>、<code v-pre>{{ content }}</code>）；级别同样以规则的级别为准。
      </p>
    </Card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.integration { max-width: 900px; }
.panel-card { margin-bottom: 16px; border-radius: 12px; }
.panel-header { margin-bottom: 8px; }
.panel-title { font-size: 15px; font-weight: 600; color: var(--aw-text); margin-bottom: 10px; letter-spacing: 0.5px; }
.panel-sub { font-size: 12px; color: var(--aw-text-faint); margin-left: 12px; }
.intro { font-size: 13px; color: var(--aw-text-secondary); line-height: 1.8; margin: 0; }

.steps { margin: 0; padding-left: 20px; }
.steps li { font-size: 13px; color: var(--aw-text-secondary); line-height: 2; }
.steps code, .notes code, .kv-val { font-family: Consolas, monospace; font-size: 12px; color: var(--aw-accent); }

.kv { display: flex; flex-direction: column; gap: 10px; }
.kv-row { display: flex; align-items: center; gap: 12px; }
.kv-key { font-size: 13px; color: var(--aw-text-secondary); width: 100px; flex-shrink: 0; }

.notes { margin: 0; padding-left: 20px; }
.notes li { font-size: 13px; color: var(--aw-text-secondary); line-height: 2; }

.code-block {
  background: var(--aw-bg-deep);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 14px;
  font-size: 12px;
  color: var(--aw-text-secondary);
  overflow: auto;
  white-space: pre;
  margin: 0;
}
.code-block code { font-family: Consolas, monospace; color: var(--aw-text-secondary); }
</style>
