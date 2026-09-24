# 前端代码地图（Frontend CodeMap）

> 用途：用户口述「哪个页面、哪个区域、什么现象」时，按此文档直接定位到文件与行号，无需全量扫描。
> 行号锚点基于 2026-08-31 的代码快照，改动后如有偏差用 `grep 函数名` 兜底。

## 1. 页面 → 文件速查表

| 页面（菜单名称） | 路由 | 文件 |
|---|---|---|
| 登录页 | `/login` | `frontend/src/views/Login.vue` |
| 仪表盘 | `/` | `frontend/src/views/Dashboard.vue` |
| 告警历史（监控中心） | `/history` | `frontend/src/views/History.vue` |
| 失败消息（监控中心） | `/failed` | `frontend/src/views/FailedMessages.vue` |
| 告警规则（通知链路） | `/rules` | `frontend/src/views/Rules.vue` |
| 消息模板（通知链路） | `/templates` | `frontend/src/views/Templates.vue` |
| 通信组（通知链路） | `/groups` | `frontend/src/views/Groups.vue` |
| 值班人员（通知链路） | `/assignees` | `frontend/src/views/Assignees.vue` |
| 通知渠道（通知链路） | `/channels` | `frontend/src/views/Channels.vue` |
| API 密钥（接入管理） | `/api-keys` | `frontend/src/views/ApiKeys.vue` |
| 节点管理（系统管理） | `/nodes` | `frontend/src/views/Nodes.vue` |

## 2. 全局壳（App.vue）

| UI 区域 | 位置 |
|---|---|
| 侧边栏 Logo（点击回仪表盘） | `App.vue:4-10` |
| 分组菜单：仪表盘 / 监控中心 / 通知链路 / 接入管理 / 系统管理 | `App.vue:11-76` |
| 菜单高亮（由路由计算 `activeMenu`） | `App.vue:98-101` |
| 顶栏：页面标题（`pageTitles` 映射） | `App.vue:79-87, 117-118` |
| 顶栏：主题切换 Dropdown（`onThemeChange`） | `App.vue:79-95, 133-137` |
| 顶栏：用户信息 + 退出登录（`onUserAction`） | `App.vue:96-106, 139-146` |
| 侧边栏折叠 `isCollapsed` / `menuitemClasses` | `App.vue:107-113` |

菜单项名称 = 路由路径（除仪表盘用 `/`），新增页面只需改路由表 + `pageTitles` 映射，无需改菜单结构（除非新增分组）。

## 3. 全局基础设施

| 文件 | 内容 | 关键行 |
|---|---|---|
| `src/main.js` | 入口：挂载 Vue + ViewUIPlus + Router，**启动时应用主题** | `applyTheme(getThemeKey())` |
| `src/theme/index.js` | 主题注册表（green-sorrow 默认 / dawn / midnight）、`applyTheme`/`getThemeKey`/`listThemes` | 全文件 |
| `src/styles/theme.css` | CSS 变量主题定义 + view-ui-plus 组件主题覆盖（按 `html[data-theme=...]` 切换） | 全文件 |
| `src/router/index.js` | 路由表 + **登录守卫**（无 token 跳 `/login`） | `router.beforeEach` |
| `src/api/request.js` | axios 工厂 + 双实例：默认实例 `request`（baseURL `/pw/alertworker/dm/api/v1`，datamanager）、`gatewayRequest`（baseURL `/pw/alertworker/gw/api/v1`，gateway）；请求拦截器带 token（localStorage `alertworker-token`，回退 `VITE_ADMIN_TOKEN`）；**401 自动清 token 跳登录页** | 全文件 |
| `vite.config.js` | **`base: '/pw/alertworker/ui/'`（前端上下文路径，多前端项目隔离）**；dev 代理按「工程+模块」前缀整段转发，无 rewrite：`/pw/alertworker/dm` → datamanager(8083)、`/pw/alertworker/gw` → gateway(8081) | 全文件 |

登录流程：`Login.vue` → `POST /pw/alertworker/dm/api/v1/auth/login` → 存 `alertworker-token` → 跳转 `redirect` query 或 `/`。
SSE 的 token 从 localStorage 读取（`History.vue:164`），不走 axios。

## 4. 各页面详细地图

### 4.1 Login（`/login`）— `views/Login.vue`

| UI 区域 | 模板行 | 逻辑 |
|---|---|---|
| 环境背景 canvas（夜空/薄雾/星空/萤火/流星/守望者/狐/猫头鹰） | `3-4` | `initCanvas` `133-437`；GSAP 入场动画 `onMounted` `104-107` |
| Canvas UI 位移特效包裹层（`<Displacement class="login-card-wrap">`，12 个 props） | `6-21` | 组件 `components/canvasui/Displacement.vue`；native / 降级分支在组件内切换 |
| 被捕获内容层（`.login-card`：透明，`ref="cardRef"`） | `22-43` | 品牌块 + 登录表单；`cardRef` 声明 `62` |
| 静态玻璃壳（`.login-card-wrap`：背景、`backdrop-filter`、inset 描边、阴影、`z-index: 3`） | 样式 `479-492` | 位于被捕获子树**之外**，毛玻璃不被栅格化破坏 |
| 尺寸测量绑定（`ResizeObserver` + `wrapStyle` computed） | — | `onMounted` `109-119`、`onUnmounted` `441`；`wrapStyle` `66-71` |
| 登录表单 + 提交 | `32-42` | `handleLogin` `82-100`（POST `/pw/alertworker/dm/api/v1/auth/login`，成功后存 token 跳转） |

- 默认账号 `admin/admin123`（后端 `AuthController`，可用 `ADMIN_USERNAME/ADMIN_PASSWORD` 覆盖）
- **尺寸测量为什么存在**：特效把卡片捕获进绝对定位的 canvas（`Displacement.vue` 原生分支），包裹层没有任何 in-flow 内容、高度归零，所以必须用 `ResizeObserver` 量出卡片尺寸再绑回 `wrapStyle`，不能靠文档流自动撑开
- **flag 门槛 + 优雅降级（重要）**：位移特效依赖实验性 WICG HTML-in-Canvas API，运行时由 `supportsHtmlInCanvas()`（`displacement-engine.js:68`）探测 `ctx.drawElementImage` + `canvas.requestPaint`。Chrome 需开 `chrome://flags/#canvas-draw-element`（等价于 `--enable-features=CanvasDrawElement`）；生产需绑定域名的 Chrome origin-trial token（当前刻意未做，owner 决策）。**无该 API 时组件按设计降级**：内容以普通 HTML 渲染、无任何报错。复测注意：无 flag 时 Blink 把 canvas 兜底子节点按 0×0 布局，包裹层高度归零——仅 stub 栅格化方法不足以走通原生路径，必须真正开 flag
- **特效只包裹登录卡片**，环境背景场景（`initCanvas`）与 GSAP 入场保持原样

### 4.2 Dashboard（`/`）— `views/Dashboard.vue`

| UI 区域 | 模板行 | 逻辑 |
|---|---|---|
| 统计卡片（24h 总消息/成功/失败/死信/已静默/未匹配） | `19-77` | 数据源 `/stats/overview`（`fetchStats`）；「已静默」「未匹配」卡片 2026-09-17 新增，两行布局（4 + 2） |
| 24 小时折线图（总消息/失败/死信/已静默/未匹配） | `79-85` | `LineChart` 组件（`components/LineChart.vue`）；数据 `/stats/24h`；全 0 的分组自动隐藏 |
| 已静默消息面板（被静默规则丢弃的告警） | `87-103` | 数据 `/stats/silenced?size=10`；`droppedColumns` 渲染 |
| 未匹配告警面板（没命中任何规则、已丢弃） | `105-127` | 数据 `/stats/unmatched?size=10`；`unmatchedColumns`（无规则名列） |
| 耗时统计（Gateway 处理 / 队列转运 / Worker 派发） | `129-153` | `TimingStat` 内联组件；数据 `/stats/timing` |
| Recent Alerts 表格（最近 10 条：ID/内容/耗时/状态/时间） | `155-166` | 数据 `/alerts/recent?size=10`；状态 Tag 渲染 `statusTag`（含 `SILENCED`/`UNMATCHED`） |

- 15 秒自动刷新（`onMounted` 定时器 `148-154`）
- 折线图为自研 SVG 组件（无第三方图表库），tooltip 跟随鼠标

### 4.3 Nodes（`/nodes`）— `views/Nodes.vue`

| UI 区域 | 模板行 | 逻辑 |
|---|---|---|
| 节点统计卡片（Gateway/Worker 总数与可用数） | `3-20` | 数据 `/nodes/summary` |
| 节点表格（ID/类型/地址/状态/可用性/权重/心跳/操作） | `25-31` | 列定义 `columns` `78-118`；`fetchAll` `126-141` |
| 操作：停用/启用、权重、测试 | `67-77` | `toggleStatus` `143-153`（PUT `/nodes/{id}/status`）、`openWeight/submitWeight`（PUT `/nodes/{id}/weight`）、`testNode`（POST `/nodes/{id}/test`） |
| 测试结果弹窗（HTTP/DB/MQ 健康度） | `45-72` | `testResult` 渲染 |

- 节点心跳由后端 gateway/worker 每 10s 上报；页面 15s 轮询
- 停用 Worker 后其监听自动暂停（后端 `WorkerListenerGuard`）

### 4.4 History（`/history`）— `views/History.vue`

| UI 区域 | 模板行 | 逻辑 |
|---|---|---|
| SSE 连接状态标签 | `4-5` | `sseConnected` |
| 表格（告警 ID/规则/状态/子状态/重试/时间/详情按钮） | `6-7` | `columns` `42-63`；`fetch` `65-71` |
| 详情弹窗（耗时链路 + 原始报文） | `10-73` | `openDetail` `80-91`（GET `/alerts/history/{id}`）；耗时计算 `msBetween` `93-102` |
| SSE 连接（token 来自 localStorage） | — | `connectSse` `163-189`；重连 `handleReconnect` `191-201` |

- 接口：`GET /alerts/history`、`GET /alerts/history/{id}`、`GET /api/v1/sse/alerts?token=...`

### 4.5 其余 CRUD 页面（API Keys / Channels / Assignees / Groups / Templates / Rules / Failed）

与历史版本一致，接口未变：
- ApiKeys：`GET/POST /api-keys`、`PUT/DELETE /api-keys/{id}`
- Channels：`GET/POST /channels`、`PUT/DELETE /channels/{id}`、`POST /channels/{id}/test`（gateway 8081）
- Assignees：`GET/POST /assignees`、`PUT/DELETE /assignees/{id}`
- Groups：`GET/POST /groups`、`PUT/DELETE /groups/{id}`
- Templates：`GET/POST /templates`、`PUT/DELETE /templates/{id}`、`POST /templates/{id}/preview`
- Rules：`GET/POST /rules`、`PUT/DELETE /rules/{id}`、`PUT /rules/{id}/silence`（静默开关）
- Failed：`GET /alerts/failed`、`GET /alerts/dead-letters`（列表含 messageNo 列）

## 5. 前端 API → 后端对应表

| 前端调用 | 后端模块 | 后端类 |
|---|---|---|
| `/auth/login`、`/auth/me` | datamanager | `AuthController` |
| `/stats/overview`、`/stats/24h`、`/stats/timing`、`/stats/silenced`、`/stats/unmatched` | datamanager | `StatsController` |
| `/nodes`、`/nodes/summary`、`/nodes/{id}/status|weight|test` | datamanager | `NodeController` |
| `/alerts/recent`、`/alerts/history*`、`/alerts/failed`、`/alerts/dead-letters`、`/alerts/delivery-logs` | datamanager | `AlertMessageController` |
| `/api-keys*` | datamanager | `ApiKeyController` |
| `/channels*`（CRUD） | datamanager | `MessageChannelController` |
| `/channels/{id}/test` | gateway | `ChannelTestController` |
| `/assignees*` | datamanager | `AssigneeController` |
| `/groups*` | datamanager | `CommunicationGroupController` |
| `/templates*` | datamanager | `MessageTemplateController` |
| `/rules*` | datamanager | `AlertRuleController` |
| `/sse/alerts` | datamanager | `AlertSseController` |
| `/api/v1/worker/test`、`/api/v1/gateway/test`（节点自检，由 NodeController 代理调用） | worker / gateway | `WorkerSelfTestController` / `GatewaySelfTestController` |

## 6. 症状 → 定位指南

| 用户描述 | 先看这里 |
|---|---|
| 「登录失败 / 一直跳登录页」 | `Login.vue handleLogin` + 后端 `AuthController`（账号密码 env）+ `request.js` 401 分支 |
| 「主题没生效 / 部分组件颜色不对」 | `src/styles/theme.css`（html[data-theme] 变量）+ `theme/index.js`；未覆盖的组件样式追加到 theme.css |
| 「折线图不显示 / 工具提示错位」 | `components/LineChart.vue`（纯 SVG，viewBox 固定 860 宽） |
| 「节点列表空 / 心跳不更新」 | 后端 gateway/worker `NodeHeartbeatService`（需 `alertworker.node.enabled=true`）+ `NodeRegistryService` |
| 「停用 Worker 后仍消费消息」 | worker `WorkerListenerGuard` + `AlertMessageConsumer` 禁用检查 |
| 「告警详情无原始报文」 | 后端 `AlertMessageController.detail`（rawPayload 字段）+ gateway `AlertController`（存 rawPayload） |
| 「耗时数据为空」 | `AlertMessageMapper.timingStatsSince`（24h 窗口内无完成态消息则 null） |
| 「历史页收不到实时消息」 | `History.vue connectSse` + 后端 `AlertSseController` |
| 「告警没发出来 / 群里收不到但历史有记录」 | 先看规则是否被静默：`Rules.vue` 静默列/`toggleSilence` → `/rules/{id}/silence` → 网关 `AlertIngestService.ingest` 静默分支；历史页状态筛选 `SILENCED` |
| 「仪表盘「已静默」卡片或面板是空的」 | `/stats/overview` 的 `silenced` + `/stats/silenced`；确认 24h 窗口内确有 `status = SILENCED` 记录（`alert_message`） |
| 「有告警进来但没人收到、历史里状态是「未匹配」」 | 没有任何规则命中 → 网关直接丢弃（`AlertIngestService.ingest` 的 `rule == null` 分支）；查 `Dashboard.vue` 未匹配面板确认报文的 labels/keys，再去 `Rules.vue` 补规则或修匹配条件 |
| 「登录页卡片没有位移特效 / 特效不生效」 | 特效依赖实验性 **HTML-in-Canvas** API：`supportsHtmlInCanvas()`（`components/canvasui/displacement-engine.js:68`）探测 `ctx.drawElementImage` + `canvas.requestPaint`。Chrome 需开 `chrome://flags/#canvas-draw-element`（等价 `--enable-features=CanvasDrawElement`）；生产需绑定域名的 origin-trial token（当前未做）。**无 API 时按设计优雅降级为普通 HTML、不报错**——先确认浏览器满足条件，再看 `components/canvasui/Displacement.vue` 的原生/降级分支 |

## 6.5 静默功能（2026-09-17 新增）

| 层 | 位置 | 说明 |
|---|---|---|
| 规则开关 UI | `Rules.vue` 表格「静默」列 + 行内「静默/恢复发送」按钮（`toggleSilence`，带二次确认） | 走 `PUT /rules/{id}/silence`，与普通编辑解耦 |
| 规则表单 | `Rules.vue` 编辑弹窗「静默」`i-switch`（`formData.silenced`） | 随规则保存提交 `silenced` |
| 规则筛选 | `Rules.vue` filter-bar「静默状态」（`filters.silenced`） | 映射到 `GET /rules?silenced=true/false` |
| 仪表盘 | `Dashboard.vue`「已静默」统计卡片 + 「已静默消息」面板 | `/stats/overview.silenced`、`/stats/silenced`；「管理规则静默」跳 `/rules` |
| 告警历史 | `History.vue` 状态筛选 `SILENCED` + 徽标 `st-silenced` | 可查全部被丢弃告警及其报文 |
| 测试告警 | `Rules.vue` 测试结果列为「已静默丢弃」橙色 Tag | 静默期间测试告警同样被丢弃，不会误发 |

后端链路：`AlertIngestService.ingest` 命中规则后先判 `rule.silenced` → 落 `SILENCED` 记录 → 直接返回，不解析接收人/渠道、不入 MQ。

## 6.6 未匹配告警（2026-09-17 新增）

没有任何规则命中时不再记为失败，而是直接丢弃并单独统计：

| 层 | 位置 | 说明 |
|---|---|---|
| 后端丢弃 | `AlertIngestService.ingest` 的 `rule == null` 分支 | 落 `UNMATCHED` 记录（`alert_rule_id` 为 NULL）→ 返回 `200`，body `{status:"UNMATCHED", unmatched:true}` |
| 后端统计 | `StatsController` `/stats/overview.unmatched`、`/stats/24h[].unmatched`、`/stats/unmatched` | 与 `SILENCED` 共用 `AlertMessageMapper.selectDroppedRecent(status, limit)` |
| 仪表盘 | `Dashboard.vue`「未匹配」统计卡片 + 「未匹配告警」面板 | 卡片与「已静默」同排（`lg=10`）；面板用 `unmatchedColumns`（无规则名，因为未匹配就是没有规则） |
| 告警历史 | `History.vue` 状态筛选 `UNMATCHED` + 徽标 `st-unmatched` | 可按状态筛出全部未匹配告警并查看原始报文 |
| 测试告警 | `Rules.vue` 测试结果列「未匹配丢弃」灰色 Tag | 手动输入报文走真实匹配时同样会被丢弃 |

判定要点：**未匹配 ≠ 失败**。`failed` 只统计 `FAILED`（网关异常 / Worker 发送失败），未匹配计入 `unmatched`，也不写 `dead_letter_message`。

## 7. 已知问题清单

1. `Groups.vue` Send Type 选项值与后端 `SendType` 枚举（`SERVICE_TYPE`/`ALL`）不匹配 → 保存组必失败
2. ~~`ApiKeys.vue` 创建表单缺少 apiValue 输入 → 422~~（2026-09-15 已改：apiValue 由后端 `ApiKeyUtil.generate()` 自动生成，表单不再有该输入；创建响应中明文仅显示一次）
3. `Rules.vue` 缺少规则→组/模板关联管理 UI
4. `Groups.vue` 缺少组成员绑定 UI
5. `FailedMessages.vue` 缺少 dead-letter 重试按钮
6. ~~`Templates.vue` 预览上下文写死 `{title, content}`，与 Prometheus 模板变量不符~~（2026-09-16 已改：默认上下文 = 完整 Alertmanager webhook 结构 + 拍平 labels/annotations + recipient/messageNo，`SAMPLE_CONTEXT` 中 `alerts[].startsAt` 动态取当前时间前 30 分钟）
7. 后端启动依赖 Flyway V2 迁移（gateway/worker/datamanager 均已开启 flyway，首启会自动执行）

## 8. 快速跳转索引

```
frontend/src/App.vue                    全局壳、分组菜单、主题切换、退出
frontend/src/main.js                    入口（应用主题）
frontend/src/router/index.js            路由表 + 登录守卫
frontend/src/api/request.js             请求封装、token、401 跳登录
frontend/src/theme/index.js             主题注册表
frontend/src/styles/theme.css           主题变量 + view-ui-plus 覆盖
frontend/src/components/LineChart.vue   自研 SVG 折线图
frontend/src/components/canvasui/rect-cache.js       矩形缓存（避免指针扫过时强制重排）
frontend/src/components/canvasui/displacement-engine.js  WebGL2 位移特效引擎（框架无关，纯 JS）
frontend/src/components/canvasui/Displacement.vue     Vue 3 包裹组件（12 个 props）
frontend/src/views/{页面名}.vue         11 个页面，见速查表
frontend/vite.config.js                 开发代理
```
