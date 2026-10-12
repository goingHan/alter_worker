# AlertWorker Worker 独立测试指南

#AI-CODE-FILE-dev_release/v0.0.1-20260810: worker 独立测试文档(配置说明 + RabbitMQ 报文格式 + 投递与验证)

> 目的：**只启动 `alertworker-worker`（端口 8082）**，绕过 gateway，直接把一条模拟报文投递到 RabbitMQ，验证 worker 的「消费 → 查规则/模板/通道 → Jinjava 渲染 → 钉钉/邮件分发 → DB 状态流转」整条链路。

---

## 1. Worker 是什么

- 独立服务，端口 **8082**，唯一入口 `WorkerApplication`（`scanBasePackages = com.alertworker`，会扫到 `alertworker-common` 里的 Mapper / Config）。
- 它只做一件事：监听 RabbitMQ 队列，消费 `AlertMessagePayload`，然后：

```
MQ 消费 → 状态置 DELIVERED
        → 按 alertRuleId 重新查 DB（alert_rule / alert_rule_template / message_template / message_channel）
        → 按接收人的 channelType 取模板（无则用 is_default=true 兜底模板）→ Jinjava 渲染
        → MessageChannelDispatcher 分发（钉钉 webhook / 邮件 SMTP）
        → 全部成功 → 状态置 success；部分失败 → 按规则重试（指数退避）→ 耗尽 → 写 dead_letter_message
```

> 重要：worker **不会**生成 `alert_message` 记录，也**不会**自己解析原始告警——这些是 gateway 干的。所以独立测试时，`alert_message` 表和关联配置需要你自己准备好（见第 5 节）。

---

## 2. 启动依赖（基础设施地址）

worker 启动时依赖三个外部组件，全部从 **Nacos** 拉取连接参数：

| 组件 | 地址 | 账号 | 用途 |
|---|---|---|---|
| Nacos 配置中心 | `172.168.1.249:28848` | `nacos` / `123.com` | 拉取 `shared-config.yaml`（datasource + rabbitmq 连接） |
| PostgreSQL | `172.168.1.238:25432/alertworker` | `airflow` / `airflow` | 规则/模板/通道/告警记录/死信（也可换 MySQL，按 JDBC URL 自动识别） |
| RabbitMQ | `172.168.1.238:5672` | `celery` / `123.com`（vhost **`alter_worker`**） | 消费报文 |

> 测试钉钉通道时，需要一个可接收的 webhook 地址（本机 mock 或真实群机器人）；测试邮件通道时才需要可用的 SMTP。

---

## 3. 配置说明

### 3.1 本地 `application.yml`（worker 自带，一般不用改）

`alertworker-worker/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: alertworker-worker
  config:
    import: nacos:shared-config.yaml?refreshEnabled=true   # 从 Nacos 拉共享配置
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:172.168.1.249:28848}
        namespace: ${NACOS_NAMESPACE:business_test}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:123.com}
        group: DEFAULT_GROUP
        file-extension: yaml
  flyway:
    enabled: false

server:
  port: 8082

# 注意：这里只放 exchange/queue/routing-key/DLX/DLQ 的"名字"。
# RabbitMQ 的连接参数（host/port/user/password/vhost）必须放在 spring.rabbitmq.* 前缀下，
# 因为 Spring Boot autoconfig 只认 spring.rabbitmq.* —— 连接参数在 Nacos 的 shared-config.yaml 里。
alertworker:
  rabbitmq:
    exchange: alertworker.exchange
    routing-key: alertworker.routing.key
    queue: alertworker.queue
    dlq: alertworker.dlq
    dlx: alertworker.dlx
```

### 3.2 Nacos 共享配置（dataId=`shared-config.yaml`，group=`DEFAULT_GROUP`，namespace=`business_test`）

仓库根目录的 `nacos-shared-config.yaml` 就是这份配置。关键内容：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://172.168.1.238:25432/alertworker
    username: airflow
    password: airflow
    driver-class-name: org.postgresql.Driver
  rabbitmq:                # ← 必须在这个前缀下，worker 才能连上 broker
    host: 172.168.1.238
    port: 5672
    username: celery
    password: 123.com
    virtual-host: alter_worker
  mail:
    host: smtp.example.com
    port: 587
    username: alerts@example.com
    password: your-email-password
    protocol: smtp
    default-encoding: UTF-8
```

### 3.3 环境变量覆盖

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `172.168.1.249:28848` | Nacos 地址 |
| `NACOS_NAMESPACE` | `business_test` | Nacos 命名空间 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | `nacos` / `123.com` | Nacos 账号（需有配置读取权限） |
| `SPRING_DATASOURCE_URL` 等 | Nacos 里配置 | 覆盖数据库连接 |
| `SPRING_RABBITMQ_HOST` 等 | Nacos 里配置 | 覆盖 MQ 连接 |

> **想完全不依赖 Nacos？** 把 `spring.config.import` 改成 `optional:nacos:shared-config.yaml?refreshEnabled=true`，再把 `spring.datasource.*` / `spring.rabbitmq.*` 内联进本地 `application.yml`（或环境变量）即可脱离 Nacos 启动。

---

## 4. 启动 Worker

```bash
cd alertworker-worker
mvn spring-boot:run
# 或直接跑已构建的 jar：
# java -jar target/alertworker-worker-1.0.0-SNAPSHOT.jar
```

启动成功后日志应出现：连上 Nacos、加载 `shared-config.yaml`、连上 PostgreSQL、连上 RabbitMQ 并监听 `alertworker.queue`。

---

## 5. 测试前数据准备（DB）

worker 消费时会按 `alertRuleId` 重新查库，因此下面这些表必须已有对应数据（通常由 DataManager 创建，这里给出最小集）：

1. **`alert_rule`**（报文里的 `alertRuleId` 必须存在且 `deleted=0`，否则消费直接失败重试）
2. **`alert_rule_template` + `message_template`**：规则关联的模板，`channel_type` 区分 `DINGDING` / `EMAIL`；至少备一个 `is_default=true` 的兜底模板
3. **`message_channel`**：钉钉通道 `config_json` 里要有 `webhookUrl`（可选 `msgType`）；邮件通道要有 `from`/`subject`/`format`
4. **`alert_message`**：**必须**先插一条，`id` 就是报文里的 `alertMessageId`。worker 依赖它做状态流转（DELIVERED→WORKER_SENT→success）和失败时的死信落库（`dead_letter_message.alert_message_id` 有外键引用 `alert_message.id`）

```sql
-- 1) 若无规则，先建一条（retry_max / retry_backoff 决定重试策略）
INSERT INTO alert_rule (id, name, alert_type, match_key, match_value, retry_max, retry_backoff)
VALUES (1, 'cpu-high', 'PROMETHEUS', 'alertname', 'CPUHigh', 3, '5s,10s,20s');

-- 2) 模板（含默认兜底模板）
INSERT INTO message_template (id, name, channel_type, content, is_default)
VALUES (1, 'dingding-default', 'DINGDING', '【告警】{{ title }}\n{{ content }}', true);
INSERT INTO alert_rule_template (rule_id, template_id, channel_type)
VALUES (1, 1, 'DINGDING');

-- 3) 钉钉通道（webhook 指向本机 mock 或真实群机器人）
--    加签机器人需带 "secret"；自定义关键字机器人带 "keyword" 数组（发送时自动前置到消息内容）
INSERT INTO message_channel (id, name, channel_type, config_json)
VALUES (1, 'dingding-test', 'DINGDING',
        '{"webhookUrl":"http://127.0.0.1:9999/hook","msgType":"text"}');
-- 真实群机器人示例：
-- '{"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","secret":"SECxxx","msgType":"text","keyword":["告警","报警"]}'

-- 4) 最关键：先插一条 alert_message，拿它的 id 作为报文 alertMessageId
INSERT INTO alert_message (api_key_id, alert_rule_id, payload, status, sub_status, retry_count)
VALUES (1, 1, '{"service_type":"test"}', 'GW_SENT', NULL, 0)
RETURNING id;   -- 记下这个 id，例如 42
```

---

## 6. 报文格式（发到 MQ 的消息长什么样）

报文是 **JSON**，对应 DTO `com.alertworker.common.dto.AlertMessagePayload`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `alertMessageId` | Long | **`alert_message.id`**，必须在 DB 中存在 |
| `alertRuleId` | Long | **`alert_rule.id`**，必须在 DB 中存在 |
| `context` | Map | 原始告警上下文，供 Jinjava 模板渲染 |
| `recipients` | List | 接收人列表（`AlertRecipient`） |

`recipients[]` 里每个元素（`AlertRecipient`）：

| 字段 | 说明 |
|---|---|
| `assigneeId` | 接收人 id（`assignee.id`，worker 不强校验） |
| `cnName` / `phone` / `dingdingUsername` | 接收人信息，模板里可用 `{{ recipient.cnName }}` 等 |
| `channelType` | `DINGDING` 或 `EMAIL`，决定用哪个模板和哪个通道处理器 |
| `messageChannelId` | 钉钉/邮件通道 id（`message_channel.id`），worker 用它取 `config_json` 里的 webhook/SMTP 配置 |

`context` 的两种形态（取决于原始告警格式）：
- **JSON 告警**：就是整个请求 body，例如 `{"service_type":"e2e","title":"...","content":"..."}`
- **Prometheus 告警**：根对象（`status`/`alerts`/`groupLabels`/...），且 `alerts[0]` 的 `labels`、`annotations` 被单独提出放进 `context.labels` / `context.annotations`

### 完整示例（含钉钉 + 邮件两个接收人）

```json
{
  "alertMessageId": 42,
  "alertRuleId": 1,
  "context": {
    "service_type": "e2e",
    "title": "CPU 使用率过高",
    "content": "node-01 CPU 使用率超过 90%"
  },
  "recipients": [
    {
      "assigneeId": 1,
      "cnName": "张三",
      "phone": "13800138000",
      "dingdingUsername": "zhangsan",
      "channelType": "DINGDING",
      "messageChannelId": 1
    },
    {
      "assigneeId": 2,
      "cnName": "李四",
      "phone": "lisi@example.com",
      "dingdingUsername": "lisi",
      "channelType": "EMAIL",
      "messageChannelId": 2
    }
  ]
}
```

### 消息属性（Headers）

gateway 生产报文时，`Jackson2JsonMessageConverter` 会自动写入这两个属性，手工投递时**建议也带上**（即便不带，worker 的 listener 方法签名也能推断出 `AlertMessagePayload` 类型，但带上更稳妥）：

```
content_type: application/json
__TypeId__:   com.alertworker.common.dto.AlertMessagePayload
```

### 路由信息（投递目标）

| 项 | 值 |
|---|---|
| Exchange | `alertworker.exchange`（direct） |
| Routing Key | `alertworker.routing.key` |
| Queue | `alertworker.queue`（durable） |
| DLX / DLQ | `alertworker.dlx` / `alertworker.dlq`（重试耗尽由 worker 直接落 `dead_letter_message` 表，不走 DLQ） |

---

## 7. 投递测试报文

### 方式 A：RabbitMQ 管理台（最简单）

1. 打开 `http://172.168.1.238:15672`，登录 `celery` / `123.com`。
2. **Queues → `alertworker.queue` → Publish message**。
3. **Properties** 填：`content_type = application/json`；**Headers** 加一行：`__TypeId__ = com.alertworker.common.dto.AlertMessagePayload`。
4. **Payload** 粘贴第 6 节的完整示例 JSON。
5. 点 **Publish message**。

### 方式 B：Python 脚本（可重复/可改参数）

```python
import json
import pika

payload = {
    "alertMessageId": 42,
    "alertRuleId": 1,
    "context": {"service_type": "e2e", "title": "CPU 使用率过高", "content": "node-01 CPU > 90%"},
    "recipients": [
        {
            "assigneeId": 1,
            "cnName": "张三",
            "phone": "13800138000",
            "dingdingUsername": "zhangsan",
            "channelType": "DINGDING",
            "messageChannelId": 1,
        }
    ],
}

creds = pika.PlainCredentials("celery", "123.com")
conn = pika.BlockingConnection(
    pika.ConnectionParameters(
        host="172.168.1.238", port=5672,
        virtual_host="alter_worker", credentials=creds,
    )
)
ch = conn.channel()
ch.basic_publish(
    exchange="alertworker.exchange",
    routing_key="alertworker.routing.key",
    body=json.dumps(payload),
    properties=pika.BasicProperties(
        content_type="application/json",
        headers={"__TypeId__": "com.alertworker.common.dto.AlertMessagePayload"},
        delivery_mode=2,
    ),
)
conn.close()
print("published")
```

> 若队列里堆积了旧消息，可先在管理台 **Queues → alertworker.queue → Purge** 清空，再投递。

---

## 8. 验证与排查

### 8.1 正常链路

- **worker 日志**：
  ```
  Received message from MQ, messageId=42
  ...（模板渲染、分发）
  ```
- **DB 状态流转**：
  ```sql
  SELECT id, status, sub_status, retry_count, updated_at FROM alert_message WHERE id = 42;
  -- 期望：recieved → GW_SENT → DELIVERED → WORKER_SENT → success
  ```
- **钉钉侧**：本机 mock（`127.0.0.1:9999`）或真实群机器人收到渲染后的消息。

### 8.2 失败/重试/死信

- 若 rule 不存在 / 模板缺失 / 通道缺失或 `config_json` 缺 `webhookUrl`，worker 会：
  `FAILED / worker_failed` → 按 `retry_backoff`（如 `5s,10s,20s`）指数退避 → 重试耗尽后写 `dead_letter_message`。
  ```sql
  SELECT * FROM dead_letter_message WHERE alert_message_id = 42;
  ```
- 手动重试死信：更新 `alert_message` 状态后重新投递同一条报文即可（当前无自动重推 DLQ 的机制）。

### 8.3 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动报「Nacos config not found」 | dataId 写错或 Nacos 账号无读权限 | 确认 dataId=`shared-config.yaml`、group=`DEFAULT_GROUP`、namespace=`business_test`，用 `nacos`/`123.com` |
| 启动报「RabbitMQ connection refused」 | 连接参数没生效 | 确认 Nacos 里是 `spring.rabbitmq.*` 前缀；vhost 是 `alter_worker`（无前导斜杠） |
| 消费后 `alertMessageId` 对应状态不更新 | `alert_message` 表没有该 id | 先插一条 `alert_message` 拿到 id，再投递 |
| 失败后死信没写入 | `dead_letter_message.alert_message_id` 外键失败 | 确保 `alert_message` 存在 |
| 钉钉没收到但日志显示发送成功 | mock 不解析 chunked 编码 | worker 的 RestTemplate 用 chunked 发送，Python `http.server` 不解析 chunked，需用 `ThreadingHTTPServer` + 手动 chunked 解析 |
| 投递时报「Unauthorized class」 | 手工投递带了错误 `__TypeId__` 或用了 Java 序列化 | body 用 JSON，`__TypeId__` 必须是 `com.alertworker.common.dto.AlertMessagePayload` |

---

## 9. 常用参考

- 消费逻辑：`alertworker-worker/.../consumer/AlertMessageConsumer.java`
- 通道分发：`alertworker-worker/.../channel/MessageChannelDispatcher.java`、`DingDingChannelHandler.java`、`EmailChannelHandler.java`
- 报文 DTO：`alertworker-common/.../dto/AlertMessagePayload.java`、`AlertRecipient.java`
- MQ 配置/JSON 转换器：`alertworker-common/.../config/RabbitMQConfig.java`
- 共享配置：仓库根目录 `nacos-shared-config.yaml`
