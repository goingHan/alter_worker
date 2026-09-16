-- ============================================================================
-- AlertWorker PostgreSQL Schema
-- #AI-CODE-FILE-dev_release/v0.0.1-20260805: generate DDL from live database catalog for self-managed table creation
--
-- 数据库: PostgreSQL
-- 说明: 本脚本从现有运行环境的 alertworker 数据库导出,可直接在空库执行建表。
--       执行方式:  psql -h <host> -p <port> -U <user> -d alertworker -f alertworker-schema.sql
--
-- 表清单:
--   alert_message           告警消息记录(完整状态生命周期: recieved/GW_SENT/DELIVERED/WORKER_SENT/FAILED/success)
--   alert_rule              告警规则(alertType=PROMETHEUS/JSON, matchKey/matchValue 匹配)
--   alert_rule_template     规则↔模板关联(每个 channel_type 一个模板)
--   api_key                 调用方 API 密钥(限流按 key 生效,默认 100 条/分钟)
--   assignee                接收人(手机号/钉钉用户名/中文名)
--   audit_log               审计日志(配置变更 + 告警处理事件)
--   communication_group     通讯组(send_type: ALL 全员 / SERVICE_TYPE 按业务类型分发)
--   dead_letter_message     死信消息(重试耗尽后落库)
--   dictionary              字典表(静态 KV)
--   flyway_schema_history   Flyway 迁移历史表(框架内部使用,应用当前 flyway 为 disabled)
--   group_assignee          通讯组↔接收人关联(含 channel_type 通知方式 + service_types 过滤)
--   message_channel         消息通道(钉钉/邮件, config_json 存 webhook/SMTP 配置)
--   message_template        消息模板(Jinjava 语法, channel_type + is_default 兜底)
--   node_registry           节点注册表(gateway/worker 心跳上报、停用/权重管理)
--   rule_group              规则↔通讯组关联
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: alert_message
-- ----------------------------------------------------------------------------
CREATE TABLE alert_message (
    id                 BIGSERIAL   NOT NULL,
    message_no         VARCHAR(64),
    api_key_id         BIGINT      NOT NULL,
    alert_rule_id      BIGINT,
    payload            TEXT        NOT NULL,
    raw_payload        TEXT,
    status             VARCHAR(32) NOT NULL,
    sub_status         VARCHAR(32),
    retry_count        INTEGER     NOT NULL DEFAULT 0,
    received_at        TIMESTAMP,
    gw_sent_at         TIMESTAMP,
    worker_received_at TIMESTAMP,
    worker_sent_at     TIMESTAMP,
    finished_at        TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: alert_rule
-- ----------------------------------------------------------------------------
CREATE TABLE alert_rule (
    id            BIGINT       NOT NULL,
    name          VARCHAR(128) NOT NULL,
    alert_type    VARCHAR(32)  NOT NULL,
    match_key     VARCHAR(128) NOT NULL,
    match_value   VARCHAR(256),
    retry_max     INTEGER      NOT NULL DEFAULT 3,
    retry_backoff VARCHAR(32)  NOT NULL DEFAULT '5,10,20',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: alert_rule_template
-- ----------------------------------------------------------------------------
CREATE TABLE alert_rule_template (
    id           BIGINT      NOT NULL,
    rule_id      BIGINT      NOT NULL,
    template_id  BIGINT      NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: api_key
-- ----------------------------------------------------------------------------
CREATE TABLE api_key (
    id         BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    api_value  VARCHAR(256) NOT NULL,
    rate_limit INTEGER      DEFAULT 100,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: assignee
-- ----------------------------------------------------------------------------
CREATE TABLE assignee (
    id                BIGINT       NOT NULL,
    phone             VARCHAR(32),
    dingding_username VARCHAR(128),
    cn_name           VARCHAR(128) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: audit_log
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT      NOT NULL,
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   BIGINT,
    operator    VARCHAR(128),
    detail      TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: communication_group
-- ----------------------------------------------------------------------------
CREATE TABLE communication_group (
    id         BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    send_type  VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: dead_letter_message
-- ----------------------------------------------------------------------------
CREATE TABLE dead_letter_message (
    id               BIGINT NOT NULL,
    alert_message_id BIGINT NOT NULL,
    error_info       TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: dictionary
-- ----------------------------------------------------------------------------
CREATE TABLE dictionary (
    id          BIGINT       NOT NULL,
    dict_type   VARCHAR(64)  NOT NULL,
    dict_key    VARCHAR(64)  NOT NULL,
    dict_value  VARCHAR(256) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: flyway_schema_history  (Flyway 框架内部表,应用当前 flyway 为 disabled)
-- ----------------------------------------------------------------------------
CREATE TABLE flyway_schema_history (
    installed_rank INTEGER        NOT NULL,
    version        VARCHAR(50),
    description    VARCHAR(200)   NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    script         VARCHAR(1000)  NOT NULL,
    checksum       INTEGER,
    installed_by   VARCHAR(100)   NOT NULL,
    installed_on   TIMESTAMP      NOT NULL DEFAULT now(),
    execution_time INTEGER        NOT NULL,
    success        BOOLEAN        NOT NULL,
    PRIMARY KEY (installed_rank)
);

-- ----------------------------------------------------------------------------
-- Table: group_assignee
-- ----------------------------------------------------------------------------
CREATE TABLE group_assignee (
    id            BIGINT      NOT NULL,
    group_id      BIGINT      NOT NULL,
    assignee_id   BIGINT      NOT NULL,
    channel_type  VARCHAR(32) NOT NULL,
    service_types TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: message_channel
-- ----------------------------------------------------------------------------
CREATE TABLE message_channel (
    id           BIGINT       NOT NULL,
    name         VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32)  NOT NULL,
    config_json  TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: message_template
-- ----------------------------------------------------------------------------
CREATE TABLE message_template (
    id           BIGINT       NOT NULL,
    name         VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32)  NOT NULL,
    content      TEXT         NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: node_registry
-- ----------------------------------------------------------------------------
CREATE TABLE node_registry (
    id                BIGSERIAL   NOT NULL,
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
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Table: rule_group
-- ----------------------------------------------------------------------------
CREATE TABLE rule_group (
    id         BIGINT    NOT NULL,
    rule_id    BIGINT    NOT NULL,
    group_id   BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- Foreign Keys
-- ----------------------------------------------------------------------------
ALTER TABLE alert_message
    ADD CONSTRAINT alert_message_alert_rule_id_fkey FOREIGN KEY (alert_rule_id) REFERENCES alert_rule(id);
ALTER TABLE alert_message
    ADD CONSTRAINT alert_message_api_key_id_fkey FOREIGN KEY (api_key_id) REFERENCES api_key(id);
ALTER TABLE alert_rule_template
    ADD CONSTRAINT alert_rule_template_rule_id_fkey FOREIGN KEY (rule_id) REFERENCES alert_rule(id);
ALTER TABLE alert_rule_template
    ADD CONSTRAINT alert_rule_template_template_id_fkey FOREIGN KEY (template_id) REFERENCES message_template(id);
ALTER TABLE dead_letter_message
    ADD CONSTRAINT dead_letter_message_alert_message_id_fkey FOREIGN KEY (alert_message_id) REFERENCES alert_message(id);
ALTER TABLE group_assignee
    ADD CONSTRAINT group_assignee_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES assignee(id);
ALTER TABLE group_assignee
    ADD CONSTRAINT group_assignee_group_id_fkey FOREIGN KEY (group_id) REFERENCES communication_group(id);
ALTER TABLE rule_group
    ADD CONSTRAINT rule_group_group_id_fkey FOREIGN KEY (group_id) REFERENCES communication_group(id);
ALTER TABLE rule_group
    ADD CONSTRAINT rule_group_rule_id_fkey FOREIGN KEY (rule_id) REFERENCES alert_rule(id);

-- ----------------------------------------------------------------------------
-- Unique Constraints
-- ----------------------------------------------------------------------------
ALTER TABLE alert_rule_template
    ADD CONSTRAINT uk_alert_rule_template UNIQUE (rule_id, channel_type);
ALTER TABLE api_key
    ADD CONSTRAINT api_key_api_value_key UNIQUE (api_value);
ALTER TABLE dead_letter_message
    ADD CONSTRAINT dead_letter_message_alert_message_id_key UNIQUE (alert_message_id);
ALTER TABLE dictionary
    ADD CONSTRAINT uk_dictionary_type_key UNIQUE (dict_type, dict_key);
ALTER TABLE node_registry
    ADD CONSTRAINT uk_node_registry_node_id UNIQUE (node_id);
ALTER TABLE group_assignee
    ADD CONSTRAINT uk_group_assignee UNIQUE (group_id, assignee_id, channel_type);
ALTER TABLE rule_group
    ADD CONSTRAINT uk_rule_group UNIQUE (rule_id, group_id);

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX idx_alert_message_api_key_id   ON alert_message USING btree (api_key_id);
CREATE INDEX idx_alert_message_created_at   ON alert_message USING btree (created_at);
CREATE INDEX idx_alert_message_received_at  ON alert_message USING btree (received_at);
CREATE INDEX idx_alert_message_status       ON alert_message USING btree (status);
CREATE INDEX idx_alert_rule_alert_type      ON alert_rule USING btree (alert_type);
CREATE INDEX idx_alert_rule_template_rule_id ON alert_rule_template USING btree (rule_id);
CREATE INDEX idx_audit_log_created_at       ON audit_log USING btree (created_at);
CREATE INDEX idx_audit_log_entity           ON audit_log USING btree (entity_type, entity_id);
CREATE INDEX idx_group_assignee_assignee_id ON group_assignee USING btree (assignee_id);
CREATE INDEX idx_group_assignee_group_id    ON group_assignee USING btree (group_id);
CREATE INDEX idx_message_template_channel_type ON message_template USING btree (channel_type);
CREATE INDEX idx_node_registry_heartbeat    ON node_registry USING btree (last_heartbeat_at);
CREATE INDEX idx_node_registry_type         ON node_registry USING btree (node_type);
CREATE INDEX idx_rule_group_group_id        ON rule_group USING btree (group_id);
CREATE INDEX idx_rule_group_rule_id         ON rule_group USING btree (rule_id);
CREATE UNIQUE INDEX uk_alert_message_no     ON alert_message USING btree (message_no);
