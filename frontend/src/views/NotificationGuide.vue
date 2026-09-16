// #AI-CODE-FILE-dev_release/v0.0.1-20260902: notification integration guide (assignees, groups, DingDing robot)
<template>
  <div class="integration">
    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">通知对接</span>
        <span class="panel-sub">配置值班人员、通信组与钉钉机器人，打通告警通知链路</span>
      </div>
      <p class="intro">
        AlertWorker 按「值班人员 → 通信组 → 告警规则 → 通知渠道」的链路，把告警精准推送到钉钉群或邮件。本页说明如何配置联系人与组，以及如何与钉钉机器人配合使用。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">接入步骤</div>
      <ol class="steps">
        <li>在钉钉群中添加「自定义机器人」，复制 <strong>Webhook 地址</strong>；如开启安全校验，同时记下<strong>加签 secret</strong>或<strong>自定义关键词</strong>。</li>
        <li>在「通知配置 → 通知渠道」新建钉钉渠道，填入 Webhook 地址、加签 secret 与消息类型。</li>
        <li>在「通知配置 → 值班人员」新建联系人，填写中文姓名、手机号、钉钉用户名。</li>
        <li>在「通知配置 → 通信组」新建通信组，选择发送类型，并绑定成员（人员 + 渠道 + 服务类型）。</li>
        <li>在「通知配置 → 告警规则」新建规则，关联该通信组。</li>
        <li>触发一条告警，即可在钉钉群收到消息，并在「告警历史」中追踪投递状态。</li>
      </ol>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">配置联系人（值班人员）</div>
      <div class="kv">
        <div class="kv-row"><span class="kv-key">中文姓名</span><code class="kv-val">cnName（必填）</code><span class="kv-desc">用于成员列表和告警记录中标识接收人</span></div>
        <div class="kv-row"><span class="kv-key">手机号</span><code class="kv-val">phone（选填）</code><span class="kv-desc">钉钉 @ 人的依据，需与钉钉账号绑定且已加入机器人所在群</span></div>
        <div class="kv-row"><span class="kv-key">钉钉用户名</span><code class="kv-val">dingdingUsername（选填）</code><span class="kv-desc">随消息进入 @ 列表；钉钉仅识别内部 userid，@ 人请以手机号为准</span></div>
      </div>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">配置通信组</div>
      <ul class="notes">
        <li><strong>名称</strong>：通信组的唯一标识。</li>
        <li><strong>发送类型</strong>：<code>ALL</code>（全部成员接收）或 <code>SERVICE_TYPE</code>（按服务类型过滤）。</li>
        <li><strong>成员绑定</strong>：为每个成员指定「人员 + 通知渠道 + 服务类型」，其中：
          <ul>
            <li>渠道：<code>DINGDING</code>（钉钉）或 <code>EMAIL</code>（邮件）。</li>
            <li>服务类型：逗号分隔，如 <code>core,edge</code>；仅当发送类型为「按服务类型」时生效。</li>
          </ul>
        </li>
        <li>同一人员可多次绑定，分别使用不同渠道或服务类型。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">service_type 过滤规则</div>
      <ul class="notes">
        <li>发送类型为 <code>ALL</code> → 所有绑定成员都接收。</li>
        <li>告警未携带 <code>service_type</code> → 所有绑定成员都接收。</li>
        <li>成员未配置「服务类型」→ 该成员始终接收。</li>
        <li>其余情况：成员配置的「服务类型」（逗号分隔）包含告警的 <code>service_type</code> 时才接收。</li>
        <li><code>service_type</code> 来源：Prometheus 告警取 label <code>service_type</code>；JSON 告警取 <code>service_type</code> 字段。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">钉钉机器人配置</div>
      <div class="kv">
        <div class="kv-row"><span class="kv-key">webhookUrl</span><code class="kv-val">必填</code><span class="kv-desc">机器人 Webhook 地址（含 access_token）</span></div>
        <div class="kv-row"><span class="kv-key">secret</span><code class="kv-val">选填</code><span class="kv-desc">加签密钥，用于机器人「加签」安全设置</span></div>
        <div class="kv-row"><span class="kv-key">msgType</span><code class="kv-val">text | markdown | action_card</code><span class="kv-desc">消息类型，默认 text</span></div>
        <div class="kv-row"><span class="kv-key">keyword</span><code class="kv-val">选填</code><span class="kv-desc">自定义关键词（字符串或数组），追加在消息末尾的小字行中</span></div>
        <div class="kv-row"><span class="kv-key">actionCard</span><code class="kv-val">选填</code><span class="kv-desc">仅 action_card 类型使用，含 title / singleTitle / singleUrl</span></div>
      </div>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">钉钉渠道 configJson 示例</div>
      <pre class="code-block"><code>{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxx",
  "secret": "SECxxxxxxxxxxxxxxxxxxxxxxxx",
  "msgType": "markdown",
  "keyword": ["告警"]
}</code></pre>
      <p class="intro" style="margin-top: 10px;">
        <code>action_card</code> 类型额外配置：
      </p>
      <pre class="code-block"><code>{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxx",
  "msgType": "action_card",
  "actionCard": {
    "title": "告警通知",
    "singleTitle": "查看详情",
    "singleUrl": "https://example.com/dashboard"
  }
}</code></pre>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">与钉钉机器人安全设置对应</div>
      <ul class="notes">
        <li><strong>自定义关键词</strong>：机器人的安全设置选「自定义关键词」时，需在 configJson 的 <code>keyword</code> 填入相同关键词；发送时关键词会追加到消息末尾。</li>
        <li><strong>加签</strong>：选「加签」时，把机器人 secret 填到 configJson 的 <code>secret</code>；发送时会自动对 URL 做 HMAC-SHA256 加签。</li>
        <li><strong>IP 白名单</strong>：选「自定义关键词」+「IP 白名单」组合时，需把 Worker 出网 IP 加入白名单。</li>
        <li>三种安全设置至少开启一种，否则机器人无法推送消息。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">钉钉 @ 人说明</div>
      <ul class="notes">
        <li><strong>合并发送</strong>：同一条告警在同一钉钉渠道只发送<strong>一条</strong>消息，并在消息中 @ 全部接收人，不会每人单独发一条。</li>
        <li><strong>@ 依据</strong>：优先使用接收人<strong>手机号</strong>（钉钉 <code>atMobiles</code>）；钉钉用户名会进入 <code>atUserIds</code>，但钉钉仅识别内部 userid，通常不生效。</li>
        <li><strong>@ 位置</strong>：@ 提及固定显示在消息末尾；被 @ 的前提是该手机号已绑定钉钉账号且已加入机器人所在的群。</li>
        <li><strong>消息尾部小字</strong>：<code>Alert ID</code> 与 <code>发送时间</code>、安全关键词在同一行显示（最小字号小字），随后一行是 @ 提及。</li>
        <li><strong>action_card 不支持 @</strong>：钉钉动作卡片消息无法携带 @ 信息，需要 @ 请使用 <code>text</code> 或 <code>markdown</code> 类型。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">常见问题</div>
      <ul class="notes">
        <li><strong>收不到消息</strong>：先看「告警历史」状态是否为 <code>success</code>；再检查钉钉机器人安全设置是否与 configJson 对应。</li>
        <li><strong>提示关键词不匹配</strong>：机器人开了「自定义关键词」，但 configJson 的 <code>keyword</code> 为空或不一致。</li>
        <li><strong>提示签名错误</strong>：机器人开了「加签」，但 configJson 的 <code>secret</code> 缺失或填错。</li>
        <li><strong>成员没收到</strong>：确认通信组发送类型与成员的「服务类型」过滤是否把该成员过滤掉了。</li>
        <li><strong>没被 @</strong>：确认其手机号已绑定钉钉账号且已加入机器人所在群；另外 <code>action_card</code> 类型不支持 @，需改用 <code>text</code> 或 <code>markdown</code>。</li>
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
.kv-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.kv-key { font-size: 13px; color: var(--aw-text-secondary); width: 100px; flex-shrink: 0; }
.kv-desc { font-size: 12px; color: var(--aw-text-faint); }

.notes { margin: 0; padding-left: 20px; }
.notes li { font-size: 13px; color: var(--aw-text-secondary); line-height: 2; }
.notes li ul { margin: 4px 0; padding-left: 20px; }
.notes li ul li { line-height: 1.9; }

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
