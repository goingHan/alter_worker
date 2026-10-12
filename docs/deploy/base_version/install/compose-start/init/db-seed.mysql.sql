-- ============================================================================
-- AlertWorker MySQL 种子数据（幂等：可重复执行，不会产生重复数据）
-- 执行时机：datamanager 首次启动完成 Flyway 建表后，由 db-seed-mysql 一次性容器执行。
-- 占位符 @SEED_API_KEY@ 由容器 entrypoint 用 .env 的 SEED_API_KEY 通过 sed 替换。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 默认钉钉消息模板（is_default = 1，未配置规则专属模板时的兜底）
--    Jinjava 语法；同时兼容 JSON 告警（title/content）与 Prometheus 告警（labels/annotations）
-- ----------------------------------------------------------------------------
INSERT INTO message_template (name, channel_type, content, is_default, deleted)
SELECT
    '默认钉钉模板',
    'DINGDING',
    CONCAT('### AlertWorker 告警', CHAR(10),
           '**标题**: {{ title }}', CHAR(10),
           '**内容**: {{ content }}', CHAR(10),
           CHAR(10),
           '**告警项**: {{ labels.alertname }}', CHAR(10),
           '**摘要**: {{ annotations.summary }}'),
    1,
    0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE is_default = 1 AND deleted = 0
);

-- ----------------------------------------------------------------------------
-- 2. 示例 API Key（值来自 db-seed-mysql 容器环境变量 SEED_API_KEY，见 .env）
--    发送告警时使用请求头 X-API-Key: <SEED_API_KEY>
-- ----------------------------------------------------------------------------
INSERT INTO api_key (name, api_value, rate_limit, deleted)
SELECT
    'default-seed-key',
    '@SEED_API_KEY@',
    100,
    0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM api_key WHERE api_value = '@SEED_API_KEY@' AND deleted = 0
);
