// #AI-CODE-FILE-dev_release/v0.0.1-20260902: notification integration guide (assignees, groups, DingDing robot)
<template>
  <div class="integration">
    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">通知对接</span>
        <span class="panel-sub">配置值班人员、通信组、模板与通知渠道，打通告警通知链路</span>
      </div>
      <p class="intro">
        AlertWorker 按「值班人员 → 通信组 → 告警规则 → 通知渠道」的链路，把告警精准推送到钉钉群或邮件。
        规则负责<strong>级别</strong>与匹配、并可开启<strong>静默</strong>、<strong>仅日报</strong>、<strong>忽略聚合</strong>；模板与渠道解耦，在绑定规则时选择。
        本页说明如何配置联系人与组，以及如何与钉钉机器人、邮件配合使用。
      </p>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">接入步骤</div>
      <ol class="steps">
        <li>在钉钉群中添加「自定义机器人」，复制 <strong>Webhook 地址</strong>；如开启安全校验，同时记下<strong>加签 secret</strong>或<strong>自定义关键词</strong>。</li>
        <li>在「通知配置 → 通知渠道」新建渠道（钉钉填 Webhook/secret/消息类型；邮件填发件人/主题/格式）。</li>
        <li>在「通知配置 → 值班人员」新建联系人，填写中文姓名、手机号、钉钉用户名、邮箱。</li>
        <li>在「通知配置 → 消息模板」新建模板（模板不绑定渠道，Jinjava 语法）。</li>
        <li>在「通知配置 → 通信组」新建通信组，选择发送类型，并绑定成员（人员 + 渠道 + 服务类型）。</li>
        <li>在「通知配置 → 告警规则」新建规则：设置级别与匹配条件，关联通信组/值班人员/渠道，并为钉钉、邮件分别绑定模板。</li>
        <li>触发一条告警，即可在钉钉群/邮件收到消息，并在「告警历史」中追踪投递状态。</li>
      </ol>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">配置联系人（值班人员）</div>
      <div class="kv">
        <div class="kv-row"><span class="kv-key">中文姓名</span><code class="kv-val">cnName（必填）</code><span class="kv-desc">用于成员列表和告警记录中标识接收人</span></div>
        <div class="kv-row"><span class="kv-key">手机号</span><code class="kv-val">phone（选填）</code><span class="kv-desc">钉钉 @ 人的依据，需与钉钉账号绑定且已加入机器人所在群</span></div>
        <div class="kv-row"><span class="kv-key">钉钉用户名</span><code class="kv-val">dingdingUsername（选填）</code><span class="kv-desc">随消息进入 @ 列表；钉钉仅识别内部 userid，@ 人请以手机号为准</span></div>
        <div class="kv-row"><span class="kv-key">邮箱</span><code class="kv-val">email（选填）</code><span class="kv-desc">邮件渠道的收件地址；留空时回退使用手机号</span></div>
        <div class="kv-row"><span class="kv-key">启用</span><code class="kv-val">enabled</code><span class="kv-desc">停用后该人员不再作为接收人</span></div>
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
        <li>规则除了关联通信组，也可直接关联值班人员（批量管理）；两者会合并去重后投递。</li>
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
      <div class="panel-title">消息模板（渠道无关）</div>
      <ul class="notes">
        <li>模板使用 <strong>Jinjava</strong> 语法，不绑定渠道；发送渠道由规则绑定模板时指定（钉钉 / 邮件）。</li>
        <li>规则可为钉钉、邮件各绑定一个模板；未绑定时回落到渠道默认模板，再回落到全局默认模板。</li>
        <li>常用变量：<code v-pre>{{ labels.alertname }}</code>、<code v-pre>{{ annotations.summary }}</code>（Prometheus）；JSON 直接取顶层字段；接收人相关可用 <code v-pre>{{ recipient.cnName }}</code>。</li>
        <li>命中规则后 <code>severity</code> 已被规则级别覆盖，模板里可直接用 <code v-pre>{{ severity }}</code>。</li>
        <li>HTML 格式模板（格式=HTML）用于日报类长文，会渲染成 HTML 页面并上传对象存储。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">告警规则选项</div>
      <ul class="notes">
        <li><strong>级别</strong>：<code>INFO</code> / <code>WARNING</code> / <code>CRITICAL</code>，规则自有级别，覆盖报文里的 <code>severity</code>。</li>
        <li><strong>匹配条件</strong>：可多条，运算符 且/或/非，自上而下左结合；值留空表示「字段存在即命中」。</li>
        <li><strong>静默</strong>：开启后命中该规则的告警（含测试告警）在网关直接丢弃，不发送任何渠道。</li>
        <li><strong>仅日报</strong>：命中告警不立即发送，入库待「告警日报」按天聚合。</li>
        <li><strong>忽略聚合</strong>：开启后即使级别为 WARNING/INFO 也立即逐条发送，不进入「告警汇总」。</li>
        <li><strong>关联渠道</strong>：可多选，同类型多条会全部发送；留空走渠道类型默认通道。</li>
        <li><strong>最大重试 / 退避</strong>：失败重试策略；重试耗尽后进入死信队列，可在「失败消息」查看与重试。</li>
      </ul>
    </Card>

    <Card :bordered="false" class="panel-card">
      <div class="panel-title">告警汇总与告警日报</div>
      <ul class="notes">
        <li><strong>告警汇总</strong>（通知配置 → 告警汇总）：开启后，<code>WARNING</code>/<code>INFO</code> 级别告警不再逐条发送，而是入库待汇总，由定时任务聚合成一个 HTML 页面统一推送（收件人取各规则联系人合并去重）。</li>
        <li>级别为 <code>CRITICAL</code> 的告警始终逐条即时发送；如需某条规则即使 WARNING/INFO 也立即发送，开启该规则的「忽略聚合」。</li>
        <li><strong>告警日报</strong>（通知配置 → 告警日报）：按 Cron 聚合所选规则当天的告警，生成 HTML 日报后发送。</li>
        <li><strong>周期测试消息</strong>（通知配置 → 周期测试消息）：按频率自动发送测试消息，用于巡检通知链路是否正常。</li>
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
      <div class="panel-title">邮件渠道 configJson 示例</div>
      <pre class="code-block"><code>{
  "from": "alert@example.com",
  "subject": "告警通知",
  "format": "html"
}</code></pre>
      <ul class="notes">
        <li><code>from</code>：发件人地址（必填）。</li>
        <li><code>subject</code>：邮件主题，默认「Alert Notification」。</li>
        <li><code>format</code>：<code>html</code> 或 <code>text</code>；HTML 日报会内嵌渲染。</li>
        <li>SMTP 服务器（host/port/账号密码）为部署级配置（Nacos / <code>spring.mail</code>），不在渠道 configJson 中填写。</li>
      </ul>
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
        <li><strong>收不到消息</strong>：先看「告警历史」状态。若为 <code>UNMATCHED</code> 说明没有规则命中；<code>SILENCED</code> 说明规则被静默；<code>WARNING_PENDING</code> 说明被「告警汇总」拦截、等待聚合；<code>success</code> 才是已投递。</li>
        <li><strong>WARNING/INFO 没有立即收到</strong>：「告警汇总」开启时这类告警会聚合发送；如需立即发送，请在对应规则开启「忽略聚合」。</li>
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
