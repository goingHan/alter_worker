# AlertWorker Docker Compose 部署工程（compose-start）

一套开箱即用的 Docker Compose 部署工程：支持一键拉起 **PostgreSQL + RabbitMQ + Nacos + 三个后端服务 + nginx（前端/API 网关）**，也可切换为使用**外部**数据库 / 消息队列 / 配置中心。内置数据持久化与自动初始化。

---

## 1. 目录结构

```
compose-start/
├── docker-compose.yml          # 编排主文件（profiles: db / mq / nacos / app）
├── .env.example                # 配置项模板 —— 复制为 .env 后按需修改
├── conf/
│   ├── nginx/
│   │   ├── default.conf        # nginx 站点配置（静态资源 + API 反代 + SSE）
│   │   └── proxy-headers.conf  # 反向代理公共请求头（被 default.conf include）
│   └── nacos/
│       └── shared-config.yaml.template  # 共享配置模板（nacos-init 容器按 .env 渲染后自动发布）
├── init/
│   └── db-seed.sql             # 幂等种子数据（默认钉钉模板、示例 API Key）
└── docker/
    ├── backend.Dockerfile      # 后端通用镜像（ARG MODULE/PORT 参数化，Maven 多阶段构建）
    └── frontend.Dockerfile     # 前端镜像（Node 构建 dist + nginx 托管）
```

---

## 2. 组件与端口

| 组件 | 容器内端口 | 宿主机端口（.env 可改） | URL 前缀 | 说明 |
|---|---|---|---|---|
| nginx | 80 | `NGINX_PORT` (默认 80) | — | **统一入口**：管理控制台 + API 反向代理 |
| datamanager | 8083 | `DM_PORT` (8083) | `/pw/alertworker/dm` | 配置 CRUD API（`/pw/alertworker/dm/api/v1/**`） |
| gateway | 8081 | `GW_PORT` (8081) | `/pw/alertworker/gw` | 告警接入 API（`/pw/alertworker/gw/api/v1/alerts/**`，Prometheus/Alertmanager 对接此服务） |
| worker | 8082 | `WORKER_PORT` (8082) | `/pw/alertworker/worker` | MQ 消费、模板渲染、钉钉/邮件投递（`/pw/alertworker/worker/api/v1/**`） |
| PostgreSQL | 5432 | `POSTGRES_PORT` | — | 主库 |
| RabbitMQ | 5672 / 15672 | `RABBITMQ_AMQP_PORT` / `RABBITMQ_MGMT_PORT` | — | 消息队列 / 管理控制台 |
| Nacos | 8848 / 9848 | `NACOS_PORT` / `NACOS_GRPC_PORT` | — | 配置中心 |

> URL 结构 = **工程前缀 `/pw/alertworker`**（用于与其他工程区分）+ **应用前缀**：前端=`/ui`（构建时 vite `base` 注入）、模块=（datamanager=`/dm`、gateway=`/gw`、worker=`/worker`，由各应用 `server.servlet.context-path` 实现）。nginx 使用 upstream 定义后端地址池，扩多节点时在 upstream 内追加 server 即可。

nginx 路由规则：

| 请求路径 | 转发目标 |
|---|---|
| `/pw/alertworker/gw/**`（告警接入、死信重试、通道测试） | upstream `alertworker_gateway` |
| `/pw/alertworker/dm/api/v1/sse/**`（SSE，关闭缓冲） | upstream `alertworker_datamanager` |
| `/pw/alertworker/dm/**` | upstream `alertworker_datamanager` |
| `/pw/alertworker/ui/**`（管理控制台 SPA） | 前端静态资源（alias dist，history 回退 index.html） |
| `/` | 302 跳转 `/pw/alertworker/ui/` |

---

## 3. 前置条件

- Docker Engine 20.10+ 与 **Docker Compose v2.20+**（使用了 `depends_on.required` 语法）
- 机器可访问镜像仓库（首次构建需联网拉取 maven 依赖与 npm 包）
- 磁盘：建议 ≥ 10GB（镜像 + 数据卷）

---

## 4. 快速开始

### 4.1 模式一：内部依赖全套（自包含部署）

```bash
cd docs/deploy/compose-start

# 1. 准备配置（生产环境务必修改密码类配置）
cp .env.example .env
vi .env

# 2. 构建并启动（首次会构建 4 个镜像，耗时较长）
docker compose --profile db --profile mq --profile nacos --profile app up -d

# 3. 观察就绪状态（全部 healthy/running 即可）
docker compose --profile db --profile mq --profile nacos --profile app ps
```

启动完成后访问 `http://<主机IP>/pw/alertworker/ui/`（nginx 会把根路径 `/` 302 过去）进入管理控制台。

### 4.2 模式二：使用外部数据库 / MQ / Nacos

前提：外部 PostgreSQL 已建库（如 `alertworker`，可为空库，schema 由应用自动迁移创建）；外部 Nacos 中允许写入 `shared-config.yaml` 配置。

```bash
# 1. 编辑 .env，把三个地址指向外部服务
DB_HOST=10.0.0.11            # 外部 PostgreSQL
RABBITMQ_HOST=10.0.0.12      # 外部 RabbitMQ
NACOS_SERVER_ADDR=10.0.0.13:28848   # 外部 Nacos
NACOS_USERNAME=xxx           # 外部 Nacos 的账号（写配置 + 应用拉配置都用它）
NACOS_PASSWORD=xxx

# 2. 只启动应用 + nginx（不启用 db / mq / nacos profile）
docker compose --profile app up -d
```

外部模式下：
- `nacos-init` 会向 `NACOS_SERVER_ADDR` 指向的外部 Nacos 发布 `shared-config.yaml`（需账号具备写权限）；若不希望自动发布（外部 Nacos 已有该配置），在 `.env` 设 `NACOS_INIT_SKIP=true`。
- 也可以只使用外部数据库而其余内置：启用 `--profile mq --profile nacos --profile app` 即可，任意组合。

> 也可单独选择应用：`docker compose --profile app up -d datamanager worker` 等，按服务名操作。

---

## 5. 配置项说明（.env）

### 5.1 时区

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `TZ` | `Asia/Shanghai` | 所有容器时区（影响日志与数据库时间戳） |

### 5.2 数据库（PostgreSQL）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | `postgres` | 应用连接的数据库地址。**内置模式保持默认（容器名）；外部模式改为外部 IP/域名** |
| `DB_PORT` | `5432` | 数据库端口 |
| `POSTGRES_DB` | `alertworker` | 库名（内置模式同时作为初始化库名） |
| `POSTGRES_USER` | `alertworker` | 用户名 |
| `POSTGRES_PASSWORD` | `alertworker` | 密码（**生产必改**） |
| `POSTGRES_PORT` | `5432` | 内置库映射到宿主机的端口（外部模式忽略） |

> 应用侧连接串由 compose 拼接为 `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}`，内置/外部两种模式共用同一配置项。

### 5.3 RabbitMQ

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `RABBITMQ_HOST` | `rabbitmq` | MQ 地址（内置=容器名；外部=外部地址） |
| `RABBITMQ_PORT` | `5672` | AMQP 端口 |
| `RABBITMQ_USERNAME` | `alertworker` | 用户名 |
| `RABBITMQ_PASSWORD` | `alertworker` | 密码（**生产必改**） |
| `RABBITMQ_VHOST` | `/` | 虚拟主机，注意不要带多余空格 |
| `RABBITMQ_AMQP_PORT` | `5672` | 内置 MQ 映射宿主机端口 |
| `RABBITMQ_MGMT_PORT` | `15672` | 内置 MQ 管理控制台映射端口 |

### 5.4 Nacos 配置中心

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `nacos:8848` | 应用与 nacos-init 连接的 Nacos 地址（`host:port`） |
| `NACOS_NAMESPACE` | `business_test` | 命名空间 **ID**（不是显示名；配置发布与应用拉取均使用它） |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | `nacos` / `123.com` | Nacos 账号（nacos-init 发布配置 + 应用拉取配置） |
| `NACOS_AUTH_ENABLE` | `false` | 仅作用于**内置** Nacos 是否开启鉴权 |
| `NACOS_INIT_SKIP` | `false` | 设为 `true` 时跳过 nacos-init 自动发布（外部 Nacos 已有配置且不想被覆盖时使用） |
| `NACOS_PORT` / `NACOS_GRPC_PORT` | `8848` / `9848` | 内置 Nacos 映射宿主机端口 |

### 5.5 SMTP 邮件（写入 Nacos 共享配置）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `MAIL_HOST` | `smtp.example.com` | SMTP 服务器 |
| `MAIL_PORT` | `587` | SMTP 端口 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 示例值 | 发件账号（使用 EMAIL 通道时必填真实值） |

### 5.6 DataManager 管理端认证

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `DM_ADMIN_TOKEN` | `alertworker-admin-2026` | 静态管理令牌（请求头 `X-Admin-Token`） |
| `DM_ADMIN_USERNAME` / `DM_ADMIN_PASSWORD` | `admin` / `admin123` | 控制台登录账号（**生产必改**） |
| `DM_AUTH_SECRET` | `alertworker-auth-secret-2026` | 登录令牌签名密钥（**生产必改为随机长字符串**） |

### 5.7 种子数据

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `SEED_API_KEY` | `alertworker-test-key` | 自动初始化的示例 API Key 值（发送告警时 `X-API-Key: <该值>`） |

### 5.8 宿主机端口映射

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `NGINX_PORT` | `80` | 统一入口端口 |
| `DM_PORT` | `8083` | datamanager 直连端口 |
| `GW_PORT` | `8081` | gateway 直连端口（外部 Prometheus/Alertmanager 对接用） |
| `WORKER_PORT` | `8082` | worker 直连端口 |

---

## 6. profiles（部署组合）

| profile | 包含服务 | 用途 |
|---|---|---|
| `db` | postgres | 内置数据库 |
| `mq` | rabbitmq | 内置消息队列 |
| `nacos` | nacos、nacos-init | 内置配置中心 + 自动发布共享配置 |
| `app` | datamanager、gateway、worker、db-seed、nacos-init、nginx | 三个后端 + 种子数据 + 配置发布 + 前端入口 |

> `nacos-init` 同时挂载 `nacos` 与 `app` 两个 profile：内置/外部 Nacos 模式下都会执行发布；不希望自动发布时在 `.env` 设 `NACOS_INIT_SKIP=true`。

常用组合：

| 场景 | 命令 |
|---|---|
| 全套内置 | `docker compose --profile db --profile mq --profile nacos --profile app up -d` |
| 全外部依赖 | `docker compose --profile app up -d`（先改 `.env` 地址） |
| 只缺数据库（用外部库） | `docker compose --profile mq --profile nacos --profile app up -d` |
| 停止全部 | `docker compose --profile db --profile mq --profile nacos --profile app down` |

> `down` 时数据卷不会删除；需彻底清空数据见 §8。

---

## 7. 自动初始化说明

首次启动时，以下初始化**自动发生**，无需人工干预：

| 阶段 | 执行者 | 行为 |
|---|---|---|
| ① Nacos 配置 | `nacos-init`（一次性容器） | 等待 Nacos 就绪 →（如配置了账号则先登录）→ 将由 `.env` 生成的 `shared-config.yaml`（数据源/Redis 无/RabbitMQ/SMTP）发布到 `NACOS_NAMESPACE` 命名空间，dataId=`shared-config.yaml`。**幂等**：重复执行只覆盖同一份配置 |
| ② 数据库 schema | datamanager 启动时（Flyway） | 自动执行 `db/migration` 全部迁移建表；gateway/worker 已关闭 Flyway，避免多服务竞争迁移 |
| ③ 种子数据 | `db-seed`（一次性容器） | 轮询等待表创建完成 → 幂等插入：默认钉钉 markdown 模板（兜底 `is_default=true`）+ 示例 API Key（`SEED_API_KEY`） |
| ④ MQ 拓扑 | 三个后端应用启动时 | Spring AMQP 自动声明 `alertworker.exchange` / `alertworker.queue` / `alertworker.dlx` / `alertworker.dlq` 及绑定关系 |

> 种子 SQL 全部使用 `WHERE NOT EXISTS` / 唯一键守卫，重复执行不会产生重复数据；`docker compose down` 后再 `up` 也不会重复插入。

---

## 8. 数据持久化与备份

### 数据卷

| 卷名 | 挂载点 | 内容 |
|---|---|---|
| `postgres_data` | `/var/lib/postgresql/data` | 全部业务数据（规则/模板/人员/告警历史/死信） |
| `rabbitmq_data` | `/var/lib/rabbitmq` | 队列、交换机、消息 |
| `nacos_data` | `/home/nacos/data` | 配置中心数据（内嵌 Derby） |

> 卷由 Docker 管理，`down` / 容器重建后数据保留。

### 备份

```bash
# 数据库备份
docker exec alertworker-postgres pg_dump -U alertworker -d alertworker > alertworker-$(date +%F).sql

# 恢复
cat alertworker-2026-09-08.sql | docker exec -i alertworker-postgres psql -U alertworker -d alertworker
```

### 彻底重置（清空所有数据）

```bash
docker compose --profile db --profile mq --profile nacos --profile app down -v
```

---

## 9. 常用运维命令

```bash
# 查看状态 / 日志
docker compose --profile db --profile mq --profile nacos --profile app ps
docker compose logs -f datamanager gateway worker

# 单独重建某个镜像（代码更新后）
docker compose --profile app build datamanager
docker compose --profile app up -d datamanager

# 全量升级
git pull
docker compose --profile db --profile mq --profile nacos --profile app build
docker compose --profile db --profile mq --profile nacos --profile app up -d

# 重新执行种子数据（不会重复插入）
docker compose --profile app up db-seed
```

> 注意：使用 profile 的命令必须始终带 `--profile` 参数，否则 compose 只操作「无 profile」的服务。

---

## 10. 部署验证

```bash
# 1. 通过 nginx 触发一条 JSON 告警（API Key 为种子数据写入的值；URL = 工程前缀 + gateway 模块前缀）
curl -X POST http://<主机IP>/pw/alertworker/gw/api/v1/alerts/json \
  -H "X-API-Key: alertworker-test-key" \
  -H "Content-Type: application/json" \
  -d '{"alertname":"DeployCheck","service_type":"ops","title":"部署验证","content":"hello alertworker"}'

# 2. 打开 http://<主机IP>/pw/alertworker/ui/ 管理控制台 → 告警历史，查看状态流转到 success
# 3. 直连 gateway 也可以：http://<主机IP>:8081/pw/alertworker/gw/api/v1/alerts/json
```

---

## 11. 常见问题（FAQ）

| 问题 | 原因 | 处理 |
|---|---|---|
| 应用反复重启，日志报 Nacos 连接失败 | 未启用 `nacos` profile 且未配置外部 `NACOS_SERVER_ADDR` | 三选一：启用 nacos profile / 配置外部地址 / （外部 Nacos）确认网络可达 |
| 应用报数据库连接失败 | 外部模式 `DB_HOST`/`DB_PORT` 不对，或外部库未建库 | 检查 `.env` 与外部库可达性；库名需与 `POSTGRES_DB` 一致 |
| `depends_on.required` 报错 | Docker Compose 版本 < v2.20 | 升级 Docker Compose |
| 告警返回 401 | `X-API-Key` 与种子/后台配置不一致 | 到控制台「API 密钥」创建/核对密钥 |
| 页面能打开但列表为空、报 401/403 | datamanager 管理端认证 | 使用 `.env` 中 `DM_ADMIN_USERNAME/PASSWORD` 登录 |
| 外部 Nacos 上发布配置失败 | 账号无写权限或鉴权信息不符 | 预先手动创建 `shared-config.yaml`（内容见 `conf/nacos/shared-config.yaml.template`，替换占位符），并设 `NACOS_INIT_SKIP=true` 跳过自动发布；应用使用已有配置不受影响 |
| Flyway 报错「multiple migrations」 | 极少见：外部库曾被旧版本服务并发迁移 | 确认只有 datamanager 开启 Flyway（compose 已默认如此） |
| SSE 实时推送断连 | 中间代理缓冲/超时 | nginx 已配置 `proxy_buffering off` 与 24h 超时；若另有外层 LB 需同样关闭缓冲 |

---

## 12. 与开发/测试环境的差异说明

- 本工程的 `.env` 与测试环境（Nacos `business_test` 下的 `shared-config.yaml`）**互不影响**：内置 Nacos 是独立实例；外部模式请在专用命名空间隔离。
- 应用镜像基于 Java 17（与项目 `pom.xml` 一致）；镜像内以非 root 用户 `app` 运行。
- 首次 `up` 需联网下载 Maven/npm 依赖；之后利用 Docker 层缓存，只在代码变更时重新构建相应层。
