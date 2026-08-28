<!-- #AI-CODE-FILE-dev_release/v0.0.1-20260805: DingDing alerting configuration guide -->

# 通过钉钉（DingDing）配置告警

本文档介绍如何在 AlertWorker 中配置**钉钉**消息通道，使告警消息能够发送到钉钉群。

## 告警链路总览

```
外部系统 ──POST /api/v1/alerts/*──▶ Gateway（校验 API Key、匹配规则）
        ──投递 RabbitMQ──▶ Worker（渲染模板、调用钉钉机器人 Webhook）
        ──▶ 钉钉群机器人 ──▶ 群成员收到告警
```

收到一条钉钉告警，需要以下配置串联起来：

| 配置项 | 作用 | 必填 |
|---|---|---|
| 钉钉群机器人 | 接收消息的入口，提供 Webhook 地址 | 是 |
| 消息通道（Channel） | 记录钉钉 Webhook 与消息类型 | 是 |
| 接收人（Assignee） | 谁接收消息（钉钉用户名/姓名/手机号） | 是 |
| 通讯组（Group） | 接收人的集合，决定通知范围 | 是 |
| 消息模板（Template） | 告警内容的渲染格式（Jinjava） | 是 |
| 告警规则（Rule） | 匹配告警、关联通讯组与模板 | 是 |
| API Key | 发送告警的身份凭证 + 限流 | 是 |

---

## 一、准备工作：创建钉钉群机器人

在钉钉中为告警创建一个**自定义机器人**，并获取其 Webhook 地址。

1. 打开目标钉钉群，点击右上角 **群设置**。
2. 进入 **智能群助手 → 添加机器人 → 自定义**。
3. 设置机器人名称（如 `AlertWorker`）。
4. **安全设置**（至少选择一种，详见下方说明）。
5. 创建完成后复制 **Webhook 地址**，形如：

   ```
   https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx
   ```

> **重要：安全设置选择**
>
> | 安全方式 | AlertWorker 是否支持 | 说明 |
> |---|---|---|
> | 自定义关键字 | ✅ 支持 | 告警内容需包含关键字才可发送 |
> | IP 地址（段） | ✅ 支持 | 限制请求来源 IP |
> | 加签（Secret） | ✅ 支持 | 在消息通道 `config_json` 里填 `secret` 字段，发送时自动做 HMAC 签名 |
>
> 三种方式可以任意组合（勾选多个时须**全部满足**）：
> - 若使用**自定义关键字**，请保证消息内容里包含关键字，否则钉钉会拒绝消息（返回 `errcode: 310000`）。钉钉最多可配 **10 个关键字**，消息**包含其中至少一个**即可发送成功。在 `config_json` 里配置 `keyword` 数组后，**发送和测试消息都会自动把关键字前置到内容里**，无需再改模板。
> - 若使用**加签**，创建机器人后复制 **Secret**（形如 `SECxxxxxxxx`），填入消息通道的 `config_json.secret`，发送时 AlertWorker 会自动按 `timestamp + "\n" + secret` 计算 HMAC-SHA256 签名并附加到 Webhook URL。

---

## 二、配置钉钉消息通道（Message Channel）

### 通过前端页面配置

1. 打开管理控制台 `http://localhost:3000`，进入 **Message Channels** 页面。
2. 点击 **Create Channel**。
3. 填写：
   - **Name**：通道名称，如 `ops-dingding`。
   - **Channel Type**：选择 `DingDing`。
   - **Config JSON**：粘贴通道配置（见下）。
4. 点击保存后，可点击列表中的 **Test** 按钮验证通道是否可用。

### Config JSON 格式

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx",
  "msgType": "markdown"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `webhookUrl` | 是 | 钉钉机器人的 Webhook 地址 |
| `secret` | 否 | 机器人的加签密钥（勾选「加签」安全设置时**必填**），发送时自动做 HMAC-SHA256 签名 |
| `msgType` | 否 | 消息类型：`text`（默认）、`markdown`、`action_card` |
| `keyword` | 否 | 机器人配置的自定义关键字，**字符串数组**（钉钉最多可配 10 个，消息包含其中至少一个即通过）。发送和测试时自动把关键字前置到消息内容，保证关键字校验通过 |
| `actionCard` | 否 | 仅 `msgType = action_card` 时使用，动作卡片按钮参数 |

### 支持的钉钉消息类型

#### 1. text —— 纯文本

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx",
  "msgType": "text"
}
```

#### 2. markdown —— Markdown 文本（推荐）

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx",
  "msgType": "markdown"
}
```

#### 3. action_card —— 动作卡片（带跳转按钮）

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx",
  "msgType": "action_card",
  "actionCard": {
    "title": "Alert",
    "singleTitle": "View Details",
    "singleUrl": "https://your-console.com/alerts"
  }
}
```

#### 加签 + 关键字组合示例

机器人同时勾选了「加签」和「自定义关键字=告警」时：

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx",
  "secret": "SECxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msgType": "markdown",
  "keyword": ["告警", "报警"]
}
```

> 注意：配置了 `keyword` 后，worker 发送和通道测试都会自动把关键字追加到消息末尾：`markdown`/`action_card` 渲染为**小标题**（`###### 告警 报警`），`text` 消息为末尾独立一行，不影响正文格式。即使不配置 `keyword`，只要消息模板渲染结果本身包含至少一个关键字，同样可以通过。

| `actionCard` 字段 | 说明 |
|---|---|
| `title` | 卡片标题（默认 `Alert`） |
| `singleTitle` | 按钮文字（默认 `View`） |
| `singleUrl` | 按钮跳转链接 |

### 通过 API 创建（等价操作）

```bash
curl -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ops-dingding",
    "channelType": "DINGDING",
    "configJson": "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxx\",\"secret\":\"SECxxxxxxxx\",\"msgType\":\"markdown\",\"keyword\":[\"告警\",\"报警\"]}"
  }'
```

> `configJson` 必须是一段 **JSON 字符串**（带转义），不是内嵌 JSON 对象。

---

## 三、创建接收人（Assignee）

接收人用于记录谁负责接收告警，字段包括：手机号、钉钉用户名、中文姓名。

### 前端页面

进入 **Assignees** 页面，创建接收人：

| 字段 | 说明 |
|---|---|
| CN Name | 中文姓名，如 `张三` |
| Phone | 手机号 |
| DingDing Username | 钉钉用户名，用于定位群成员 |

### API

```bash
curl -X POST http://localhost:8080/api/v1/assignees \
  -H "Content-Type: application/json" \
  -d '{
    "cnName": "Zhang San",
    "phone": "13800138000",
    "dingdingUsername": "zhangsan"
  }'
```

---

## 四、创建通讯组（Communication Group）并添加成员

通讯组是接收人的集合，决定告警发给哪些人。

### 1. 创建通讯组

进入 **Groups** 页面，点击创建：

| 字段 | 说明 |
|---|---|
| Name | 组名，如 `ops-team` |
| Send Type | 通知方式：`ALL` 或 `SERVICE_TYPE` |

- `ALL`：组内所有成员都接收所有告警。
- `SERVICE_TYPE`：按 `service_type` 过滤，只有匹配该服务类型的成员才接收，且可配置每个人的通知方式。

### 2. 添加成员

在通讯组详情中为组添加成员：

| 字段 | 说明 |
|---|---|
| Assignee | 选择已创建的接收人 |
| Channel Type | 选择 `DINGDING`（该成员通过钉钉接收） |
| Service Types | `Send Type = SERVICE_TYPE` 时填写，如 `order-service,payment-service` |

> 一个接收人可以通过多个 `channel_type` 加入组（例如同时用钉钉和 Email 接收），此时按 `channel_type` 分行配置。

---

## 五、创建消息模板（Message Template）

模板决定钉钉告警的最终文本内容，使用 **Jinjava**（Jinja2 的 Java 实现）语法，可引用告警字段。

### 前端页面

进入 **Templates** 页面，点击创建：

| 字段 | 说明 |
|---|---|
| Name | 模板名，如 `alert-dingding` |
| Channel Type | 选择 `DingDing` |
| Content | Jinjava 模板内容 |
| Is Default | 是否作为默认模板（回退模板） |

### 模板内容示例（markdown）

```jinjava
## {{ title }}

- **级别**：{{ level }}
- **服务**：{{ service_type }}
- **内容**：{{ content }}

> 告警时间：{{ timestamp }}
```

可用变量为发送告警时传入的字段（如 `title`、`content`、`level`、`service_type`）以及接收人信息（`recipient.cn_name`）。

> **默认模板**：当某规则没有为 `DINGDING` 配置专属模板时，会回退到默认模板。建议先创建一个 `channelType = DINGDING` 的默认模板，保证任何规则都能正常发送。

### 预览模板

在模板列表点击 **Preview**，可传入测试上下文查看渲染结果：

```json
{
  "title": "CPU High",
  "content": "Usage 95%"
}
```

---

## 六、创建告警规则（Alert Rule）

规则负责「收到什么样的告警 → 发给谁、用什么模板」。

### 1. 创建规则

进入 **Rules** 页面，点击创建：

| 字段 | 说明 |
|---|---|
| Name | 规则名，如 `cpu-alert` |
| Alert Type | 告警格式：`PROMETHEUS`（Prometheus webhook）或 `JSON`（自定义 JSON） |
| Match Key | 匹配字段：`PROMETHEUS` 用 label 名；`JSON` 用顶层 key 名 |
| Match Value | 匹配值（可选，不填则匹配该字段任意值） |
| Retry Max | 最大重试次数（默认 3） |
| Retry Backoff | 重试退避，逗号分隔的秒数，如 `5,10,20` |

### 2. 关联通讯组

在规则详情中 **Add Group**，选择要接收此告警的通讯组。

### 3. 关联模板

在规则详情中 **Add Template**，为该规则指定钉钉模板：

```json
{
  "templateId": 1,
  "channelType": "DINGDING"
}
```

> 同一个规则可以为不同 `channelType`（`DINGDING` / `EMAIL`）配置不同模板；发送时按接收人的通道选择对应模板。

### 匹配逻辑说明

- **PROMETHEUS**：从告警 `alerts[0].labels` 中取 `matchKey` 对应的值，与 `matchValue` 比较。
- **JSON**：从 JSON 顶层取 `matchKey` 对应的值，与 `matchValue` 比较。
- 若通讯组使用 `SERVICE_TYPE`，还会从告警中提取 `service_type`（Prometheus 的 label 或 JSON 顶层 key）做成员过滤。

---

## 七、创建 API Key

发送告警需要 API Key 作为凭证，并受限流控制（默认 100 条/分钟/Key）。

进入 **API Keys** 页面创建：

| 字段 | 说明 |
|---|---|
| Name | Key 名称，如 `production` |
| API Value | Key 值，发送告警时放入 `X-API-Key` 请求头 |
| Rate Limit | 每分钟限流次数（默认 100） |

---

## 八、测试验证

### 1. 通道测试

在 **Message Channels** 列表点击某钉钉通道的 **Test** 按钮（或调用 `POST /api/v1/channels/{id}/test`）。成功时钉钉群会收到一条测试消息 `[AlertWorker] Channel test message`。

### 2. 发送一条 JSON 告警

```bash
curl -X POST http://localhost:8081/api/v1/alerts/json \
  -H "X-API-Key: your-api-key-value" \
  -H "Content-Type: application/json" \
  -d '{
    "alertname": "CPUHigh",
    "service_type": "order-service",
    "level": "warning",
    "title": "CPU Usage High",
    "content": "CPU usage on server-01 is 95%"
  }'
```

> 需保证 `alertname`（`matchKey`）与规则的 `matchValue` 匹配；若通讯组使用 `SERVICE_TYPE`，需带上 `service_type`。

### 3. 发送一条 Prometheus 告警

```bash
curl -X POST http://localhost:8081/api/v1/alerts/prometheus \
  -H "X-API-Key: your-api-key-value" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "firing",
    "alerts": [
      {
        "labels": {
          "alertname": "CPUHigh",
          "service_type": "order-service",
          "severity": "critical"
        },
        "annotations": {
          "summary": "High CPU usage",
          "description": "CPU above 85% for 5 minutes"
        }
      }
    ]
  }'
```

### 4. 查看结果

- 钉钉群内应收到对应的告警消息。
- 管理控制台 **Alert History** 页面可看到消息状态流转：
  `recieved → GW_SENT → DELIVERED → WORKER_SENT → success`
- 若失败，可到 **Failed Messages / Dead Letters** 页面查看失败原因。

---

## 附：常见问题

| 问题 | 可能原因 | 解决方法 |
|---|---|---|
| 通道 Test 失败 | Webhook 地址错误 / 网络不通 / 安全设置不满足 | 确认 `webhookUrl` 完整正确；加签机器人需填 `secret`，关键字机器人需填 `keyword` 数组 |
| 钉钉返回 `errcode: 310000` | 关键字不匹配 / 加签失败 / IP 白名单 | 在 `config_json` 配置 `keyword` 数组（发送时自动前置）或让模板内容包含至少一个关键字；加签机器人确认 `config_json.secret` 与机器人 Secret 一致；放行 Worker 出口 IP |
| 群内没收到消息但状态为 `success` | 消息被钉钉静默丢弃 | 核对安全设置与模板关键字 |
| 只有部分人收到 | 通讯组 `SERVICE_TYPE` 过滤生效 | 检查成员的 `service_types` 与告警 `service_type` 是否匹配 |
| `action_card` 发送失败 | 钉钉 `actionCard` 字段格式要求 | 确认 `actionCard` 配置项齐全，必要时对照钉钉文档核对请求体格式 |
| 告警状态停在 `GW_SENT` | Worker 未消费 | 检查 RabbitMQ 连接与 Worker 日志 |
| 401 | API Key 无效或缺失 | 确认 `X-API-Key` 请求头与 Key 值 |
