// #AI-CODE-FILE-dev_release/v0.0.1-20260901: Prometheus integration guide page
<template>
  <div class="integration">
    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">Prometheus 对接</span>
        <span class="panel-sub">通过 Alertmanager Webhook 将告警推送到 AlertWorker</span>
      </div>
      <p class="intro">
        AlertWorker 以 HTTP 接口接收 Prometheus Alertmanager 转发过来的告警，根据「告警规则」匹配后路由到钉钉 / 邮件等通知渠道。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">接入步骤</div>
      <ol class="steps">
        <li>在「接入管理 → API 密钥」中创建一个 API 密钥，记下它的<strong>密钥值</strong>。</li>
        <li>在「通知配置 → 告警规则」中创建一条规则：告警类型选 <code>Prometheus</code>，匹配 Key 填要匹配的 label 名（如 <code>alertname</code>）。</li>
        <li>在 Prometheus Alertmanager 配置文件中新增一个 <code>webhook</code> 接收器，指向下面的地址。</li>
        <li>触发一条告警，即可在「告警历史」中看到消息并追踪投递状态。</li>
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
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">匹配规则说明</div>
      <ul class="notes">
        <li><strong>告警类型 Prometheus</strong>：按告警第一个 <code>labels</code> 中的 label 匹配。</li>
        <li><strong>匹配 Key</strong>：要匹配的 label 名，如 <code>alertname</code>、<code>severity</code>。</li>
        <li><strong>匹配值</strong>：期望的值，如 <code>CPUHigh</code>；留空表示匹配任意值。</li>
        <li><strong>多条件</strong>：一条规则可配置多个匹配条件，命中任一条件即匹配该规则。</li>
        <li><strong>service_type</strong>：从 label <code>service_type</code> 中读取，用于按服务类型过滤通知人员。</li>
      </ul>
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
