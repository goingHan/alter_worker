-- ============================================================================
-- AlertWorker 种子数据（幂等：可重复执行，不会产生重复数据）
-- 执行时机：datamanager 首次启动完成 Flyway 建表后，由 db-seed 一次性容器执行。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 默认钉钉消息模板（is_default = true，未配置规则专属模板时的兜底）
--    Jinjava 语法；同时兼容 JSON 告警（title/content）与 Prometheus 告警（labels/annotations）
-- ----------------------------------------------------------------------------
INSERT INTO message_template (name, channel_type, content, is_default, deleted)
SELECT
    '默认钉钉模板',
    'DINGDING',
    '### AlertWorker 告警' || chr(10) ||
    '**标题**: {{ title }}' || chr(10) ||
    '**内容**: {{ content }}' || chr(10) ||
    chr(10) ||
    '**告警项**: {{ labels.alertname }}' || chr(10) ||
    '**摘要**: {{ annotations.summary }}',
    TRUE,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM message_template WHERE is_default = TRUE AND deleted = 0
);

-- ----------------------------------------------------------------------------
-- 2. 示例 API Key（值来自 db-seed 容器环境变量 SEED_API_KEY，见 .env）
--    发送告警时使用请求头 X-API-Key: <SEED_API_KEY>
-- ----------------------------------------------------------------------------
INSERT INTO api_key (name, api_value, rate_limit, deleted)
SELECT
    'default-seed-key',
    :'seed_api_key',
    100,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM api_key WHERE api_value = :'seed_api_key' AND deleted = 0
);
