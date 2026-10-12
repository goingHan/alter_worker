-- V1 (MySQL): initial schema.
-- Types mapped for MySQL 8: BIGSERIAL -> BIGINT AUTO_INCREMENT, BOOLEAN -> TINYINT(1),
-- TIMESTAMP -> DATETIME, large free-text -> LONGTEXT. Inline REFERENCES become table-level
-- FOREIGN KEY constraints (MySQL requires them to be enforced); engine/charset are utf8mb4.

CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    api_value VARCHAR(256) NOT NULL,
    rate_limit INT DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_key_value (api_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS message_channel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS assignee (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(32),
    dingding_username VARCHAR(128),
    cn_name VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS communication_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    send_type VARCHAR(32) NOT NULL DEFAULT 'ALL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS group_assignee (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    service_types LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_assignee (group_id, assignee_id, channel_type),
    KEY idx_group_assignee_group_id (group_id),
    KEY idx_group_assignee_assignee_id (assignee_id),
    CONSTRAINT fk_group_assignee_group FOREIGN KEY (group_id) REFERENCES communication_group (id),
    CONSTRAINT fk_group_assignee_assignee FOREIGN KEY (assignee_id) REFERENCES assignee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS message_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_message_template_channel_type (channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    match_key VARCHAR(128) NOT NULL,
    match_value VARCHAR(256),
    retry_max INT NOT NULL DEFAULT 3,
    retry_backoff VARCHAR(32) NOT NULL DEFAULT '5,10,20',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_alert_rule_alert_type (alert_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rule_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_group (rule_id, group_id),
    KEY idx_rule_group_rule_id (rule_id),
    KEY idx_rule_group_group_id (group_id),
    CONSTRAINT fk_rule_group_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT fk_rule_group_group FOREIGN KEY (group_id) REFERENCES communication_group (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS alert_rule_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_rule_template (rule_id, channel_type),
    KEY idx_alert_rule_template_rule_id (rule_id),
    CONSTRAINT fk_art_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT fk_art_template FOREIGN KEY (template_id) REFERENCES message_template (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS alert_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    api_key_id BIGINT NOT NULL,
    alert_rule_id BIGINT,
    payload LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sub_status VARCHAR(32),
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_alert_message_status (status),
    KEY idx_alert_message_api_key_id (api_key_id),
    KEY idx_alert_message_created_at (created_at),
    CONSTRAINT fk_alert_message_api_key FOREIGN KEY (api_key_id) REFERENCES api_key (id),
    CONSTRAINT fk_alert_message_rule FOREIGN KEY (alert_rule_id) REFERENCES alert_rule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS dead_letter_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_message_id BIGINT NOT NULL,
    error_info LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dead_letter_alert_message (alert_message_id),
    CONSTRAINT fk_dlm_alert_message FOREIGN KEY (alert_message_id) REFERENCES alert_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT,
    operator VARCHAR(128),
    detail LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_log_entity (entity_type, entity_id),
    KEY idx_audit_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS dictionary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_type VARCHAR(64) NOT NULL,
    dict_key VARCHAR(64) NOT NULL,
    dict_value VARCHAR(256) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dictionary_type_key (dict_type, dict_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
