```sql
CREATE TABLE `tax_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `state_no` varchar(64) NOT NULL DEFAULT '' COMMENT '状态码加密值',
  `tax_package_info_id` bigint NOT NULL DEFAULT '0' COMMENT '付税id',
  `tax_package_pay_no` varchar(64) NOT NULL DEFAULT '' COMMENT '付税号',
  `tax_package_info_time` datetime DEFAULT NULL COMMENT '付税时间',
  `tax_pay_status` int NOT NULL DEFAULT '0' COMMENT '付税状态',
  `first_notice_time` datetime DEFAULT NULL COMMENT '首次提醒时间',
  `paid_time` datetime DEFAULT NULL COMMENT '付款完成时间',
  `transfer_bank` int NOT NULL DEFAULT '0' COMMENT '转账银行',
  `transfer_bank_time` datetime DEFAULT NULL COMMENT '设置转账银行的时间',
  `invoce_url` varchar(128) NOT NULL DEFAULT '' COMMENT '发票下载地址',
  `send_sms_cnt` int NOT NULL DEFAULT '0' COMMENT '发送短信次数',
  `send_email_cnt` int DEFAULT '0' COMMENT '发送邮件次数',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人ID',
  `modified_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `modified_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_tax_package_info_id` (`tax_package_info_id`),
  KEY `idx_tax_init_time_status` (`tax_package_info_time`,`tax_pay_status`)
) ENGINE=InnoDB COMMENT='付税信息表';
```

# 规则
## 物理主键统一 (id)
每一张表现在都拥有一个自增的 `bigint` 类型物理主键 `id`。这是 MySQL 索引性能优化和分库分表的基础。
## 字段一致性 (NOT NULL)
字段既然设置了默认值，则必须声明为 `NOT NULL`。
## 时间字段规范
时间字段统一 `datetime(3)`。
## 默认值强制性
字段除主键外，必须都要设置默认值，数字型的默认 0，字符串型的默认 ''，如果是枚举则依实际业务而定默认值。
## 文档完整性
`COMMENT` 必须有。

每个表设计时必须包含以下字段，以下时间字段默认值固定以下方式
```sql
  `version` int NOT NULL DEFAULT '0' COMMENT '版本',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人ID',
  `modified_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `modified_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人ID',
```