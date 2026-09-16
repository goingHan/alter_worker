-- ============================================================================
-- AlertWorker V2 DDL：消息追踪 + 节点注册表（升级脚本）
-- 对应 Flyway 迁移：alertworker-common/src/main/resources/db/migration/V2__message_trace_and_node_registry.sql
--
-- 数据库: PostgreSQL
-- 说明: 本脚本用于在已有 V1 结构的 alertworker 数据库上增量升级（幂等，可重复执行）。
--       应用启动时 Flyway 会自动执行同款语句；本脚本供手动升级 / 无法自动迁移的环境使用。
--       执行方式:  psql -h <host> -p <port> -U <user> -d alertworker -f alertworker-v2-ddl.sql
--
-- 变更点:
--   1. alert_message 新增字段:
--        message_no           消息唯一 ID（AW+时间戳+随机码，回传给调用方并随告警发送给外部系统）
--        raw_payload          用户发送的原始报文（回查用）
--        received_at          Gateway 接收时间
--        gw_sent_at           Gateway 写入 MQ 时间
--        worker_received_at   Worker 从 MQ 接收时间
--        worker_sent_at       Worker 发送外部系统时间
--        finished_at          处理完成（成功/失败）时间
--   2. 新增表 node_registry: gateway/worker 节点注册（心跳、停用、权重）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. alert_message 新增列
-- ----------------------------------------------------------------------------
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS message_no         VARCHAR(64);
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS raw_payload        TEXT;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS received_at        TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS gw_sent_at         TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS worker_received_at TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS worker_sent_at     TIMESTAMP;
ALTER TABLE alert_message ADD COLUMN IF NOT EXISTS finished_at        TIMESTAMP;

-- 历史数据回填（message_no 用记录 ID 兜底，received_at 用 created_at 兜底）
UPDATE alert_message SET message_no  = 'AW-' || id    WHERE message_no  IS NULL;
UPDATE alert_message SET received_at = created_at     WHERE received_at IS NULL;

-- ----------------------------------------------------------------------------
-- 2. 消息唯一 ID 约束与索引
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_message_no          ON alert_message USING btree (message_no);
CREATE INDEX        IF NOT EXISTS idx_alert_message_received_at ON alert_message USING btree (received_at);

-- ----------------------------------------------------------------------------
-- 3. 节点注册表 node_registry
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS node_registry (
    id                BIGSERIAL    NOT NULL,
    node_id           VARCHAR(128) NOT NULL,
    node_type         VARCHAR(32)  NOT NULL,
    host              VARCHAR(128),
    port              INTEGER,
    status            VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    weight            INTEGER      NOT NULL DEFAULT 1,
    last_heartbeat_at TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT pk_node_registry      PRIMARY KEY (id),
    CONSTRAINT uk_node_registry_node UNIQUE (node_id)
);

COMMENT ON TABLE node_registry IS 'gateway/worker 节点注册表: 心跳上报、停用/启用、调度权重';
COMMENT ON COLUMN node_registry.node_id           IS '节点唯一标识（hostname:port）';
COMMENT ON COLUMN node_registry.node_type         IS '节点类型: GATEWAY / WORKER';
COMMENT ON COLUMN node_registry.status            IS '节点状态: ENABLED / DISABLED（停用后 Worker 暂停消费）';
COMMENT ON COLUMN node_registry.weight            IS '调度权重: 0 不参与调度，数值越大优先级越高';
COMMENT ON COLUMN node_registry.last_heartbeat_at IS '最近心跳时间（10 秒上报一次，30 秒无心跳判离线）';

CREATE INDEX IF NOT EXISTS idx_node_registry_type      ON node_registry USING btree (node_type);
CREATE INDEX IF NOT EXISTS idx_node_registry_heartbeat ON node_registry USING btree (last_heartbeat_at);
