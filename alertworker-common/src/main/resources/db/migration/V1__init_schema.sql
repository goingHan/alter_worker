CREATE TABLE IF NOT EXISTS api_key (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    api_value VARCHAR(256) NOT NULL UNIQUE,
    rate_limit INT DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS message_channel (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS assignee (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(32),
    dingding_username VARCHAR(128),
    cn_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS communication_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    send_type VARCHAR(32) NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS group_assignee (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES communication_group(id),
    assignee_id BIGINT NOT NULL REFERENCES assignee(id),
    channel_type VARCHAR(32) NOT NULL,
    service_types TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_group_assignee UNIQUE (group_id, assignee_id, channel_type)
);

CREATE INDEX idx_group_assignee_group_id ON group_assignee(group_id);
CREATE INDEX idx_group_assignee_assignee_id ON group_assignee(assignee_id);

CREATE TABLE IF NOT EXISTS message_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_message_template_channel_type ON message_template(channel_type);

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    match_key VARCHAR(128) NOT NULL,
    match_value VARCHAR(256),
    retry_max INT NOT NULL DEFAULT 3,
    retry_backoff VARCHAR(32) NOT NULL DEFAULT '5,10,20',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_alert_rule_alert_type ON alert_rule(alert_type);

CREATE TABLE IF NOT EXISTS rule_group (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES communication_group(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rule_group UNIQUE (rule_id, group_id)
);

CREATE INDEX idx_rule_group_rule_id ON rule_group(rule_id);
CREATE INDEX idx_rule_group_group_id ON rule_group(group_id);

CREATE TABLE IF NOT EXISTS alert_rule_template (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES message_template(id) ON DELETE CASCADE,
    channel_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_rule_template UNIQUE (rule_id, channel_type)
);

CREATE INDEX idx_alert_rule_template_rule_id ON alert_rule_template(rule_id);

CREATE TABLE IF NOT EXISTS alert_message (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL REFERENCES api_key(id),
    alert_rule_id BIGINT REFERENCES alert_rule(id),
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sub_status VARCHAR(32),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_alert_message_status ON alert_message(status);
CREATE INDEX idx_alert_message_api_key_id ON alert_message(api_key_id);
CREATE INDEX idx_alert_message_created_at ON alert_message(created_at);

CREATE TABLE IF NOT EXISTS dead_letter_message (
    id BIGSERIAL PRIMARY KEY,
    alert_message_id BIGINT NOT NULL UNIQUE REFERENCES alert_message(id),
    error_info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT,
    operator VARCHAR(128),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

CREATE TABLE IF NOT EXISTS dictionary (
    id BIGSERIAL PRIMARY KEY,
    dict_type VARCHAR(64) NOT NULL,
    dict_key VARCHAR(64) NOT NULL,
    dict_value VARCHAR(256) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_dictionary_type_key UNIQUE (dict_type, dict_key)
);
