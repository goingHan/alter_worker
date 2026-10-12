# AlertWorker 手动部署文档（jar + 前端 + nginx）

本文档描述**不使用 Docker**、在服务器上以 jar 包 + 静态文件 + nginx 的方式手动部署 AlertWorker 的完整流程。

> Docker Compose 部署方式见同目录 [`compose-start/README.md`](./compose-start/README.md)。
>
> **URL 结构**（2026-09-08 起生效）：`http://<host>:<port>/pw/alertworker/<模块前缀>/api/v1/**`
> 工程前缀 `/pw/alertworker` + 模块前缀（datamanager=`/dm`、gateway=`/gw`、worker=`/worker`，由各应用 `server.servlet.context-path` 实现）。**前端与后端版本必须同步升级**（路径强耦合）。

---

## 1. 组件与端口总览

| 组件 | 端口 | URL 前缀 | 说明 |
|---|---|---|---|
| nginx | 80 | `/pw/alertworker/ui`（前端） | 统一入口：前端静态资源 + API 反向代理（upstream 形式） |
| datamanager | 8083 | `/pw/alertworker/dm` | 配置 CRUD API、登录认证 |
| gateway | 8081 | `/pw/alertworker/gw` | 告警接入（Prometheus/Alertmanager 对接）、死信重试、通道测试 |
| worker | 8082 | `/pw/alertworker/worker` | MQ 消费、模板渲染、钉钉/邮件投递 |
| PostgreSQL | 5432 | — | 主库（schema 由 datamanager 的 Flyway 自动创建） |
| MySQL（可选） | 3306 | — | 备选主库（按 JDBC URL 自动识别数据库类型） |
| RabbitMQ | 5672 / 15672 | — | 消息队列（拓扑由应用自动声明） |
| Nacos | 8848 | — | 配置中心（**应用启动硬依赖**） |

依赖部署：PostgreSQL / RabbitMQ / Nacos 可复用现有环境（参考根目录 `docker-compose.yml` 或公司已有实例），本文聚焦 AlertWorker 自身。

---

## 2. 前置条件

- JDK 17+（`java -version` 确认）
- nginx 1.20+
- 可达的 PostgreSQL / RabbitMQ / Nacos
- 构建机（可跳过）：JDK 17 + Maven 3.8+、Node.js 18+ + npm

## 3. 构建产物

**已有现成 jar 与 dist 的话跳过本节。**

```bash
# 后端（在项目根目录）
mvn clean package -DskipTests
# 产物：
#   alertworker-datamanager/target/alertworker-datamanager-1.0.0-SNAPSHOT.jar
#   alertworker-gateway/target/alertworker-gateway-1.0.0-SNAPSHOT.jar
#   alertworker-worker/target/alertworker-worker-1.0.0-SNAPSHOT.jar

# 前端
cd frontend
npm ci
npm run build
# 产物：frontend/dist/
```

---

## 4. 服务器目录布局（建议）

```
/opt/alertworker/
├── datamanager/
│   ├── alertworker-datamanager-1.0.0-SNAPSHOT.jar
│   ├── config/application.yml        # 可选：外置配置（见 §6）
│   ├── logs/
│   └── start.sh / stop.sh            # 见 §5.3
├── gateway/
│   ├── alertworker-gateway-1.0.0-SNAPSHOT.jar
│   ├── config/application.yml
│   ├── logs/
│   └── start.sh / stop.sh
├── worker/
│   ├── alertworker-worker-1.0.0-SNAPSHOT.jar
│   ├── config/application.yml
│   ├── logs/
│   └── start.sh / stop.sh
└── frontend/
    └── dist/                          # 前端编译产物
```

上传方式示例：

```bash
scp alertworker-*/target/alertworker-*-SNAPSHOT.jar user@server:/opt/alertworker/<模块>/
scp -r frontend/dist user@server:/opt/alertworker/frontend/
```

---

## 5. 后端启动

### 5.1 前置：Nacos 共享配置

三个应用启动时都会从 Nacos 拉取 `shared-config.yaml`（数据源 / RabbitMQ / 邮件），拉取失败则无法启动。首次部署需保证 Nacos 中存在该配置：

- **方式 A（推荐）**：使用 compose 工程的 `nacos-init` 容器自动发布（见 compose-start README §7），或按其模板 [`compose-start/conf/nacos/shared-config.yaml.template`](./compose-start/conf/nacos/shared-config.yaml.template) 在 Nacos 控制台手工创建（dataId=`shared-config.yaml`，group=`DEFAULT_GROUP`，namespace=你的命名空间，占位符 `@VAR@` 手工替换为真实值）。
- **方式 B**：不用 Nacos 存业务配置——仍然必须有 Nacos 可达，但数据库/MQ 等全部用环境变量或外置文件覆盖（见 §6，环境变量优先级高于 Nacos 导入）。

> 若启用 **HTML 日报**（数据加工模块 `alertworker-processor` 渲染并上传对象存储），还需在 `shared-config.yaml` 中补充 `alertworker.storage.*`（OSS bucket / endpoint / 公网基址 / AK-SK）。完整配置说明见 [对象存储配置](../../../design/object-storage-config.md)。
>
> ⚠️ processor 的本地 `application.yml` **不再定义 `alertworker.storage`**（2026-10-09 变更），运行时以 Nacos 为唯一来源；**不要**再用 `STORAGE_*` / `OSS_*` 环境变量——OS 环境变量优先级高于 Nacos，会遮蔽 Nacos 导致日报仍上传到旧桶。临时覆盖请用标准 `ALERTWORKER_STORAGE_*`。

### 5.2 启动命令

> **时区要求**：JVM 时区必须是 `Asia/Shanghai`（后端以 CST 墙钟时间落库与展示，历史数据已由 Flyway `V12` 一次性 +8h 校正）。启动时加 `-Duser.timezone=Asia/Shanghai`，或在环境变量中设 `TZ=Asia/Shanghai`（Docker 部署由 compose 自动注入 `TZ`）。

**datamanager（先启动，负责建表）：**

```bash
cd /opt/alertworker/datamanager
NACOS_SERVER_ADDR=172.168.1.249:28848 \
NACOS_NAMESPACE=business_test \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=123.com \
NODE_HOST=<本机对外主机名或IP> \
DATAMANAGER_ADMIN_TOKEN=alertworker-admin-2026 \
ADMIN_USERNAME=admin \
ADMIN_PASSWORD=admin123 \
ADMIN_AUTH_SECRET=alertworker-auth-secret-2026 \
nohup java -jar alertworker-datamanager-1.0.0-SNAPSHOT.jar \
  > logs/out.log 2>&1 &
```

**gateway：**

```bash
cd /opt/alertworker/gateway
NACOS_SERVER_ADDR=172.168.1.249:28848 \
NACOS_NAMESPACE=business_test \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=123.com \
NODE_HOST=<本机对外主机名或IP> \
SPRING_FLYWAY_ENABLED=false \
nohup java -jar alertworker-gateway-1.0.0-SNAPSHOT.jar \
  > logs/out.log 2>&1 &
```

**worker：**

```bash
cd /opt/alertworker/worker
NACOS_SERVER_ADDR=172.168.1.249:28848 \
NACOS_NAMESPACE=business_test \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=123.com \
NODE_HOST=<本机对外主机名或IP> \
SPRING_FLYWAY_ENABLED=false \
nohup java -jar alertworker-worker-1.0.0-SNAPSHOT.jar \
  > logs/out.log 2>&1 &
```

关键环境变量说明：

| 变量 | 说明 |
|---|---|
| `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE` / `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 连接（namespace 填 **ID**） |
| `SPRING_FLYWAY_ENABLED=false` | **gateway/worker 必须关闭**，仅 datamanager 执行建表，避免迁移竞争 |
| `NODE_HOST` | 节点管理页显示的主机名；节点测试接口按 `http://<NODE_HOST>:<port>/<前缀>/...` 探测，务必填可解析地址 |
| `DATAMANAGER_ADMIN_TOKEN` / `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_AUTH_SECRET` | datamanager 管理端认证（生产必改） |
| `SPRING_DATASOURCE_URL` / `SPRING_RABBITMQ_HOST` 等 | 覆盖 Nacos 里的同名配置（优先级更高，见 §6） |
| `TZ` / `-Duser.timezone` | JVM 时区，**必须为 `Asia/Shanghai`**（见 §5.2 顶部说明） |

> 对象存储（processor）运行时以 Nacos 的 `alertworker.storage.*` 为准；临时覆盖用 `ALERTWORKER_STORAGE_*`（见 [对象存储配置](../../../design/object-storage-config.md)）。`STORAGE_*` / `OSS_*` 仅由 compose 的 `nacos-init` 用于渲染 Nacos，**不要**注入到 processor 容器。

### 5.3 启停脚本示例

`start.sh`（以 gateway 为例，其余模块同理）：

```bash
#!/bin/bash
APP=alertworker-gateway-1.0.0-SNAPSHOT.jar
cd "$(dirname "$0")"
if [ -f gateway.pid ] && kill -0 "$(cat gateway.pid)" 2>/dev/null; then
  echo "already running, pid=$(cat gateway.pid)"; exit 0
fi
NACOS_SERVER_ADDR=172.168.1.249:28848 \
NACOS_NAMESPACE=business_test \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=123.com \
NODE_HOST=$(hostname) \
SPRING_FLYWAY_ENABLED=false \
nohup java -jar "$APP" > logs/out.log 2>&1 &
echo $! > gateway.pid
echo "started, pid=$(cat gateway.pid)"
```

`stop.sh`：

```bash
#!/bin/bash
cd "$(dirname "$0")"
[ -f gateway.pid ] && kill "$(cat gateway.pid)" && rm gateway.pid && echo stopped
```

> 生产建议改用 systemd 托管（`Restart=on-failure`），示例如下：
>
> ```ini
> # /etc/systemd/system/alertworker-gateway.service
> [Unit]
> Description=AlertWorker Gateway
> After=network.target
> [Service]
> User=app
> WorkingDirectory=/opt/alertworker/gateway
> Environment=NACOS_SERVER_ADDR=172.168.1.249:28848
> Environment=NACOS_NAMESPACE=business_test
> Environment=SPRING_FLYWAY_ENABLED=false
> Environment=NODE_HOST=<对外地址>
> ExecStart=/usr/bin/java -jar alertworker-gateway-1.0.0-SNAPSHOT.jar
> Restart=on-failure
> [Install]
> WantedBy=multi-user.target
> ```

---

## 6. 外置配置与改配置

配置优先级（高 → 低）：

```
① 启动参数 --key=value
② 环境变量（SPRING_DATASOURCE_URL、NACOS_* 等）
③ jar 同级 ./config/application.yml      ← 推荐
④ jar 内 application.yml
⑤ Nacos shared-config.yaml
```

**方式③（推荐）**：在 jar 旁建 `config/application.yml`，Spring Boot 自动加载并覆盖 jar 内同名配置。示例：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://172.168.1.238:25432/alertworker
    username: airflow
    password: airflow
  rabbitmq:
    host: 172.168.1.238
    port: 5672
    username: celery
    password: 123.com
    virtual-host: alter_worker

alertworker:
  node:
    host: anmin            # 节点管理页展示的主机名
```

改配置后需重启对应进程生效（Nacos 共享配置改动有 `refreshEnabled`，gateway/worker 可热生效部分配置）。

---

## 7. 前端部署

```bash
# 把构建好的 dist 上传到 /opt/alertworker/frontend/dist（见 §4）
# 无需任何 Node 运行时，纯静态文件，由 nginx 托管
```

前端挂载在**上下文路径 `/pw/alertworker/ui/`** 下（与工程前缀一致，便于同一 nginx 下部署多个前端项目）：

- 构建时 `vite.config.js` 的 `base: '/pw/alertworker/ui/'` 已把产物内资源引用全部带上此前缀，**dist 上传后无需任何修改**；
- 页面入口为 `http://<服务器IP>/pw/alertworker/ui/`，根路径 `/` 由 nginx 302 跳转过去；
- 路由（vue-router）与 401 跳登录均已按 base 处理，部署时零额外配置。

后端 API 为同域相对路径（`/pw/alertworker/dm/api/v1` 与 `/pw/alertworker/gw/api/v1`），必须与后端同域，由 nginx 反向代理打通。

---

## 8. nginx 配置（upstream 形式）

使用 upstream 定义后端地址：未来扩多节点时在 upstream 内追加 `server` 行即可，location 无需改动。

两份文件（与 compose 工程同源，upstream 内地址换成 `127.0.0.1` 即为宿主机版本）：

`/etc/nginx/alertworker-proxy-headers.conf`：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_http_version 1.1;
```

`/etc/nginx/conf.d/alertworker.conf`：

```nginx
# ---------- upstream：后端地址池（扩容时在此追加节点） ----------
upstream alertworker_datamanager {
    server 127.0.0.1:8083;
    # server <第二节点>:8083;
}

upstream alertworker_gateway {
    server 127.0.0.1:8081;
    # server <第二节点>:8081;
}

server {
    listen 80;
    server_name _;

    client_max_body_size 10m;
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;

    # ---------- gateway：告警接入 / 死信重试 / 通道测试 ----------
    location /pw/alertworker/gw/ {
        proxy_pass http://alertworker_gateway;
        include /etc/nginx/alertworker-proxy-headers.conf;
        proxy_read_timeout 60s;
    }

    # ---------- SSE 实时推送（datamanager，必须关闭缓冲） ----------
    location /pw/alertworker/dm/api/v1/sse/ {
        proxy_pass http://alertworker_datamanager;
        include /etc/nginx/alertworker-proxy-headers.conf;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
        proxy_read_timeout 24h;
    }

    # ---------- datamanager：配置 CRUD / 告警历史 ----------
    location /pw/alertworker/dm/ {
        proxy_pass http://alertworker_datamanager;
        include /etc/nginx/alertworker-proxy-headers.conf;
        proxy_read_timeout 300s;
    }

    # ---------- 前端 SPA（/pw/alertworker/ui/） ----------
    # 注意 ^~：优先于正则 location；否则通用静态缓存正则（~* \.(js|css|svg)...$）
    # 会把带哈希的 js/css/svg 截走到默认 root，导致 404
    location ^~ /pw/alertworker/ui/assets/ {
        alias /opt/alertworker/frontend/dist/assets/;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location ^~ /pw/alertworker/ui/ {
        alias /opt/alertworker/frontend/dist/;
        index index.html;
        try_files $uri $uri/ /pw/alertworker/ui/index.html;
        # index.html 协商缓存：发版即生效；带哈希的长缓存只给 assets
        add_header Cache-Control "no-cache";
    }

    # 根路径跳转到控制台
    location = / {
        return 302 /pw/alertworker/ui/;
    }
}
```

```bash
nginx -t && systemctl reload nginx
```

路由原理：
- `proxy_pass http://upstream名;` 不带 URI，`/pw/alertworker/gw`、`/pw/alertworker/dm` 前缀原样透传，与后端 context-path 一一对应，nginx 无需任何 rewrite；
- 前端用 `alias` 把 `/pw/alertworker/ui/` 映射到 dist 目录，`try_files` 回退到该前缀下的 `index.html`（vue-router history 模式依赖）；
- **`^~` 必须保留**：nginx 中正则 location 优先于普通前缀 location，若服务器里存在通用静态缓存正则（如 `~* \.(js|css|svg)$`）而没有 `^~`，前端所有 js/css/svg 都会被它截走（该 location 内无 root/alias → 404）。

> **worker 不经 nginx**：它只消费 MQ 并外发通知，`/pw/alertworker/worker/**` 仅供网内调用（节点管理页测试、调试接口）。

---

## 9. 验证

```bash
# 1) datamanager 存活（带管理令牌）
curl -H "X-Admin-Token: alertworker-admin-2026" \
     http://127.0.0.1:8083/pw/alertworker/dm/api/v1/nodes

# 2) 经 nginx 触发一条 JSON 告警（需已建 API Key / 规则 / 组）
curl -X POST http://127.0.0.1/pw/alertworker/gw/api/v1/alerts/json \
  -H "X-API-Key: <你的key>" \
  -H "Content-Type: application/json" \
  -d '{"alertname":"DeployCheck","service_type":"ops","title":"部署验证","content":"hello"}'

# 3) 打开 http://<服务器IP>/pw/alertworker/ui/ 登录控制台（账号见 §5.2 ADMIN_*），
#    在「告警历史」查看状态流转 recieved → GW_SENT → DELIVERED → WORKER_SENT → success
```

---

## 10. 常见问题

| 问题 | 原因 | 处理 |
|---|---|---|
| 启动报 Nacos 连接失败 | `NACOS_SERVER_ADDR`/账号不对或网络不通 | 核对环境变量；namespace 填 ID 不是显示名 |
| 接口全部 404 | 前后端版本不一致（URL 前缀强耦合） | 前后端同步升级；确认访问路径带 `/pw/alertworker/<模块>` |
| datamanager 接口 401/403 | 管理端认证 | 登录获取令牌或带 `X-Admin-Token`；核对 `ADMIN_*` 环境变量 |
| gateway 报 401 | `X-API-Key` 缺失/无效 | 控制台「API 密钥」创建后再调 |
| 建表报错/重复迁移 | 多个服务同时开 Flyway | gateway/worker 设 `SPRING_FLYWAY_ENABLED=false` |
| 页面能开但请求 404 | nginx 未配置对应前缀反代 | 核对 §8 的 `/pw/alertworker/gw`、`/pw/alertworker/dm` 两个 location |
| 控制台打开 404 / 白屏 | 未从 `/pw/alertworker/ui/` 进入，或 dist 版本未带 base | 根路径 `/` 会 302 到 `/pw/alertworker/ui/`；确认 dist 是加 base 后构建的产物 |
| **index.html 正常但 js/css/favicon 全部 404** | 通用静态缓存正则（`~* \.(js|css|svg)$`）优先级高于普通前缀 location，把请求截走到无 root 的 location | SPA 的两个 location 必须加 `^~`（见 §8），或确保 alias 所在前缀块优先于正则 |
| 刷新子页面 404 | alias/try_files 配置缺失 | 保留 §8 中 `location /pw/alertworker/ui/` 的 alias + try_files（history 模式回退） |
| SSE 断连/收不到实时推送 | 代理缓冲 | 保留 `/dm/api/v1/sse/` 的 `proxy_buffering off`；外层若有 LB 需同样处理 |
| 节点管理页「测试」失败 | `NODE_HOST` 不可解析 | 填网内可达的 IP/主机名 |
