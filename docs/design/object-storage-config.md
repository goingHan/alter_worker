<!-- #AI-CODE-FILE-dev_release/v0.0.2-20261009: object storage configuration guide for hosted HTML daily reports -->

# 对象存储（Object Storage）配置

本文档介绍 AlertWorker 的**对象存储配置**：HTML 格式日报渲染成文件后上传到哪里、公网访问地址如何生成、配置放在哪里、以及如何本地开发与排障。

适用对象：运维 / 部署人员、需要开启 HTML 日报的开发者。

---

## 一、用途与设计原则

- **用途**：仅用于**日报**。当日报模板 `message_template.format = HTML` 时，数据加工模块（`alertworker-processor`）会把渲染结果写成一个**自包含 HTML 文件**并上传到对象存储，消息里携带该文件的公网链接。
  - 邮件：内嵌 HTML 正文。
  - 钉钉：发送链接（`action_card` 用按钮 `singleURL`，`markdown`/`text` 追加链接文本）。
- **不涉及**普通告警与周期测试消息（它们仍走内联模板渲染）。
- **适配器模式**：上传与访问解耦——上传走 OSS `endpoint` + `bucket`，访问走独立的 `public-base-url`。当前实现：
  - `AliyunOssStorage`（生产，阿里云 OSS）
  - `LocalFileStorage`（本地开发，落盘）
  - 后续可扩展 S3 / MinIO 等，无需改动业务代码。

> **关键点**：上传地址与最终呈现 URL 可能不同。例如上传 endpoint 是 `oss-cn-beijing.aliyuncs.com`，但最终访问地址是 `https://chatfiles.tydiczt.com/...`。

---

## 二、架构与数据流

```
[alertworker-processor]  cron 到点 / 手动「立即生成」
   └─ 组装日报 context
   └─ 解析模板：format = HTML
   └─ 渲染 Jinjava → 自包含 HTML
   └─ ObjectStorageService.put(objectKey, bytes)   ← 上传（endpoint + bucket）
   └─ ObjectStorageService.publicUrl(objectKey)     ← 生成公网链接（public-base-url + key，不含 bucket）
   └─ alert_message 记录 content_url / object_key
   └─ 经 RabbitMQ 发送 kind=REPORT（renderedContent + htmlUrl）
        ↓
[alertworker-worker]
   ├─ EMAIL   ：内嵌 HTML
   └─ DINGDING：action_card 按钮 / markdown·text 追加链接
```

涉及模块：

| 模块 | 职责 |
|---|---|
| `alertworker-common` | 适配器接口 `ObjectStorageService` + 配置属性 `StorageProperties` |
| `alertworker-processor` | 日报渲染与上传、`AliyunOssStorage` / `LocalFileStorage` 实现（context-path `/pw/alertworker/dp`） |
| `alertworker-worker` | 按渠道投递（邮件内嵌 / 钉钉链接） |

---

## 三、配置项

所有配置位于 `alertworker.storage.*`（前缀由 `StorageProperties` 绑定）。

| 配置键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `alertworker.storage.type` | 否 | `local` | 适配器选择：`oss`（阿里云 OSS）\| `local`（本地落盘） |
| `alertworker.storage.public-base-url` | oss 必填 | 无 | **公网访问基址**（可与上传地址不同），可含路径前缀 |
| `alertworker.storage.bucket` | oss 必填 | 无 | OSS Bucket 名称（**仅用于上传**，不拼进公网 URL） |
| `alertworker.storage.home` | 否 | 无 | 对象键前缀（桶内目录），如 `tmp_digital` |
| `alertworker.storage.local-root` | 否 | `alertworker-storage` | 本地适配器落盘根目录 |
| `alertworker.storage.oss.endpoint` | oss 必填 | 无 | OSS 上传 endpoint，如 `oss-cn-beijing.aliyuncs.com` |
| `alertworker.storage.oss.region` | 否 | 无 | OSS 区域，如 `cn-beijing` |
| `alertworker.storage.oss.access-key-id` | oss 必填 | 无 | 访问密钥 AccessKeyId（**不要提交进 git**） |
| `alertworker.storage.oss.access-key-secret` | oss 必填 | 无 | 访问密钥 AccessKeySecret（**不要提交进 git**） |

临时覆盖用的环境变量（`@ConfigurationProperties` 宽松绑定，映射到 `alertworker.storage.*`）：

| 环境变量 | 映射配置 |
|---|---|
| `ALERTWORKER_STORAGE_TYPE` | `alertworker.storage.type` |
| `ALERTWORKER_STORAGE_PUBLIC_BASE_URL` | `alertworker.storage.public-base-url` |
| `ALERTWORKER_STORAGE_BUCKET` | `alertworker.storage.bucket` |
| `ALERTWORKER_STORAGE_HOME` | `alertworker.storage.home` |
| `ALERTWORKER_STORAGE_LOCAL_ROOT` | `alertworker.storage.local-root` |
| `ALERTWORKER_STORAGE_OSS_ENDPOINT` | `alertworker.storage.oss.endpoint` |
| `ALERTWORKER_STORAGE_OSS_REGION` | `alertworker.storage.oss.region` |
| `ALERTWORKER_STORAGE_OSS_ACCESS_KEY_ID` | `alertworker.storage.oss.access-key-id` |
| `ALERTWORKER_STORAGE_OSS_ACCESS_KEY_SECRET` | `alertworker.storage.oss.access-key-secret` |

> ⚠️ processor 的 `application.yml` **不再定义 `alertworker.storage`**（2026-10-09 变更）。历史上它用 `${STORAGE_TYPE:...}`、`${STORAGE_BUCKET:nbchat-prod}` 等占位符引用 `STORAGE_*` / `OSS_*`，而 OS 环境变量优先级高于 Nacos——只要 shell / 容器里残留 `STORAGE_BUCKET=nbchat-prod`，就会**遮蔽 Nacos** 导致日报仍上传到旧桶。现改为：运行时只认 Nacos，临时覆盖用上面的 `ALERTWORKER_STORAGE_*`。

---

## 四、对象键（objectKey）与公网 URL 映射

规则固定如下（由 `StorageProperties#objectKey` / `#publicUrl` 实现）：

```
objectKey  = home + "/" + relative
publicUrl  = public-base-url + "/" + objectKey
```

> `bucket` **只用于上传**（OSS API 需要），**不拼进公网 URL** —— 自定义域名已指向 bucket 根。

- 日报的相对键 `relative` 为：`reports/{yyyy}/{MM}/{dd}/{messageNo}.html`
- `messageNo` 格式 `REPORT-<雪花ID>`，**每次日报运行唯一**（MQ 重试复用同一 messageNo，覆盖同一对象 → 天然幂等）。

**示例**（本环境）：`home=tmp_ops_reports`、`bucket=nbchat-test`、`public-base-url=https://chatfiles-test.tydiczt.com`

```
objectKey  = tmp_ops_reports/reports/2026/10/10/REPORT-896703190596288512.html
publicUrl  = https://chatfiles-test.tydiczt.com/tmp_ops_reports/reports/2026/10/10/REPORT-896703190596288512.html
```

> URL 中**不含 bucket 名**（自定义域名已指向 bucket 根）。`bucket` 仅用于上传；请确保 `public-base-url` 与域名映射一致，否则会出现 404。

---

## 五、配置来源与优先级

AlertWorker 使用 **Nacos** 作为配置中心，各模块通过 `spring.config.import: nacos:shared-config.yaml` 拉取共享配置。

优先级（高 → 低）：

1. **操作系统环境变量**（如 `ALERTWORKER_STORAGE_TYPE`、`ALERTWORKER_STORAGE_BUCKET`）
2. **Nacos `shared-config.yaml`**（`spring.config.import` 导入，**优先于**导入它的 `application.yml`）
3. **`StorageProperties` 字段默认值**（`type=local`，其余为空 → 本地落盘）

> 结论：**生产环境以 Nacos 为准**；processor 本地 `application.yml` 不再定义 `alertworker.storage`，因此不存在本地默认值遮蔽 Nacos 的情况；临时覆盖用 `ALERTWORKER_STORAGE_*` 环境变量。

---

## 六、配置方式

### 6.1 Nacos 共享配置（生产 / 共享环境，推荐）

在 `shared-config.yaml`（`group=DEFAULT_GROUP`）中增加：

```yaml
alertworker:
  storage:
    type: oss
    public-base-url: https://chatfiles.tydiczt.com
    bucket: nbchat-prod
    home: tmp_digital
    oss:
      endpoint: oss-cn-beijing.aliyuncs.com
      region: cn-beijing
      access-key-id: <OSS_ACCESS_KEY_ID>          # 用真实值，勿提交进 git
      access-key-secret: <OSS_ACCESS_KEY_SECRET>  # 用真实值，勿提交进 git
```

> ⚠️ 各环境命名空间（如 `business_test`、`release`）需**分别**发布该段。

**发布方式：**

- **A. 复用部署工程的 `nacos-init` 容器（推荐）**：把 `.env` 的 `OSS_ACCESS_KEY_*` 等填好、`NACOS_SERVER_ADDR` 指向目标 Nacos，重跑 `nacos-init`，由 `shared-config.yaml.template` 渲染发布（分别用 `NACOS_NAMESPACE=business_test` / `release` 各跑一次）。
- **B. Nacos 控制台 / OpenAPI 手动追加**：见 [部署文档](../deploy/base_version/install/README.md)。

### 6.2 Docker 部署（.env → nacos-init → Nacos）

配置链路与 datasource/mail/rabbitmq 完全一致：

```
compose-start/.env  ──▶  nacos-init 容器（sed 渲染模板）  ──▶  Nacos shared-config.yaml  ──▶  各应用
```

- `.env` 只用于喂给 `nacos-init` 渲染 Nacos（`STORAGE_TYPE` / `STORAGE_PUBLIC_BASE_URL` / `STORAGE_BUCKET` / `STORAGE_HOME` / `OSS_ENDPOINT` / `OSS_REGION` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`）
- 模板：`docs/deploy/base_version/install/compose-start/conf/nacos/shared-config.yaml.template`
- `docker-compose.yml` 中 `processor` 服务**不再**注入 `STORAGE_*` / `OSS_*` 环境变量（自 2026-10-09）：OS 环境变量优先级高于 Nacos，注入会遮蔽 Nacos。存储配置一律由 `nacos-init` 写入 Nacos 后由应用读取；如确需临时覆盖，改用 `ALERTWORKER_STORAGE_*`。

### 6.3 本地开发（不上传 OSS）

processor 的 `application.yml` 不再定义 `alertworker.storage`，未配置时 `StorageProperties.type` 默认即 `local`，直接使用本地适配器，无需任何 OSS 凭证。如需显式指定：

```bash
# 或通过环境变量（优先级最高）
ALERTWORKER_STORAGE_TYPE=local
ALERTWORKER_STORAGE_LOCAL_ROOT=alertworker-storage
```

> 若连了 Nacos 且 Nacos 里配了 `type=oss`，本地会走 OSS；此时用上面的 `ALERTWORKER_STORAGE_TYPE=local` 覆盖即可。

上传后的文件落在 `${local-root}/{objectKey}`，例如：
`alertworker-storage/reports/2026/10/09/REPORT-xxx.html`（`home` 为空时不带前缀）。

> 本地适配器的公网地址优先取 `public-base-url`；未配置时返回 `file://...` 本地路径。

---

## 七、适配器实现与扩展

接口（`alertworker-common`）：

```java
public interface ObjectStorageService {
    String put(String objectKey, byte[] content, String contentType);
    String publicUrl(String objectKey);
    boolean exists(String objectKey);
    void delete(String objectKey);
}
```

实现选择（`@ConditionalOnProperty(prefix="alertworker.storage", name="type", ...)`）：

| 实现 | 生效条件 | 说明 |
|---|---|---|
| `LocalFileStorage` | `type=local`（或未配置） | 落盘到 `local-root`，用于开发 |
| `AliyunOssStorage` | `type=oss` | 阿里云 OSS SDK（`aliyun-sdk-oss`） |

**新增存储后端（如 S3 / MinIO）**：实现 `ObjectStorageService`，并在类上标注 `@ConditionalOnProperty(prefix="alertworker.storage", name="type", havingValue="s3")`，新增对应配置项即可，业务代码（日报执行器）无需改动。

---

## 八、模板格式与投递行为

- 模板新增 `format` 字段：`TEXT`（默认，内联发送）| `HTML`（渲染成文件并上传）。
- 迁移 `V14__html_report_templates.sql` 内置了一个只读模板 **`内置-HTML日报模板`**（`format=HTML`、渠道无关，自包含样式）。可在「告警日报」的「模板」下拉中选择（下拉会以 `（HTML）` 标注）。
- 日报模板解析顺序：日报显式 `templateId` → 内置 HTML 报表模板 → 内置报表模板 → 全局默认模板。

投递行为（`alertworker-worker`）：

| 渠道 | HTML 日报 |
|---|---|
| 邮件（EMAIL） | 内嵌渲染好的 HTML 正文 |
| 钉钉（DINGDING） | `action_card`：按钮 `singleURL = 公网链接`；`markdown`/`text`：正文后追加「查看完整内容」链接 |

历史记录：`alert_message.content_url` / `object_key` 保存文件位置；**告警历史 → 详情**会出现「归档：查看完整日报」链接。

---

## 九、验证与运维

1. 触发一次日报：告警日报列表「立即生成」，或 `POST /pw/alertworker/dp/api/v1/reports/{id}/run`。
2. 确认为 HTML 模板：模板格式为 `HTML`。
3. 启动日志会打印一次生效配置：`Effective object storage config: type=oss bucket=... home=... publicBaseUrl=...`；生成时出现 `Daily report id=... uploaded HTML report to objectKey=... contentUrl=...`。用这两条日志核对 bucket/home 是否与预期一致。
4. 在**告警历史 → 详情**查看 `content_url` 并点击链接验证可访问。
5. 本地模式：检查 `${local-root}/...` 落地文件。
6. 处理器自检：`GET /pw/alertworker/dp/api/v1/processor/test`（返回 `storage` 实现名、健康状态，以及 `storageConfig` 中的 `type/bucket/home/publicBaseUrl`）。

### 常见问题

| 问题 | 可能原因 | 解决 |
|---|---|---|
| 链接 404 | `public-base-url` 与域名映射不一致（自定义域名应指向 bucket 根） | 按第四节规则核对；URL 不含 bucket，形如 `public-base-url/home/...` |
| 链接 403 / 拒绝访问 | Bucket 非公共读 | 将 Bucket（或对象前缀）设为公共读；本方案不做签名 URL |
| 上传失败 `403 AccessDenied` | AK/SK 无效、权限不足 | 检查 `access-key-id` / `access-key-secret` 及 RAM 授权 |
| 上传失败 `InvalidEndpoint` | endpoint 写成了带 `https://` 的完整地址 | endpoint 只填域名，如 `oss-cn-beijing.aliyuncs.com` |
| 本地模式链接是 `file://` | 未配置 `public-base-url` | 本地模式可忽略；如需可访问配置 `public-base-url` |
| Nacos 里改了 storage 不生效 | 残留 `STORAGE_*` / `OSS_*` OS 环境变量遮蔽；或改了非 processor 读取的 namespace；或未重启 | 看启动日志 `Effective object storage config`；确认无 `STORAGE_BUCKET` 等 env（改用 `ALERTWORKER_STORAGE_*`）；确认 namespace=`business_test`/dataId=`shared-config.yaml`；`StorageProperties` 非 `@RefreshScope`，改 Nacos 后需**重启** processor |
| 邮件收到 HTML 源码 / 钉钉显示大段 HTML | 模板 `format` 仍为 `TEXT` | 将模板格式改为 `HTML` |

---

## 十、相关文件索引

| 文件 | 说明 |
|---|---|
| `alertworker-common/.../storage/ObjectStorageService.java` | 适配器接口 |
| `alertworker-common/.../storage/StorageProperties.java` | 配置属性与 key/URL 拼接规则 |
| `alertworker-processor/.../storage/AliyunOssStorage.java` | OSS 实现 |
| `alertworker-processor/.../storage/LocalFileStorage.java` | 本地实现 |
| `alertworker-processor/.../scheduler/DailyReportJobExecutor.java` | 日报渲染 + 上传 |
| `alertworker-processor/src/main/resources/application.yml` | 不再定义 `alertworker.storage`（Nacos 为唯一来源） |
| `alertworker-worker/.../service/AlertMessageProcessor.java` | 邮件内嵌 / 钉钉链接投递 |
| `alertworker-common/.../db/migration/V14__html_report_templates.sql` | `format` 列、`content_url/object_key`、内置 HTML 模板 |
| `nacos-shared-config.yaml` | 手动上传 Nacos 的共享配置源文件 |
| `docs/deploy/base_version/install/compose-start/conf/nacos/shared-config.yaml.template` | Docker 部署模板 |
| `docs/deploy/base_version/install/compose-start/.env.example` | 环境变量样例 |
| `docs/deploy/base_version/install/config_sample/dp_application.yml` | processor 配置样例 |
