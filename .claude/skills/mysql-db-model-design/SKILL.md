---
name: mysql-db-model-design
description: 当用户要求设计、创建或修改 MySQL 数据模型/数据库表时，必须激活此技能。它确保所有表结构符合 Buffalo 项目的统一 MySQL 规范（包括强制字段、命名约定和默认值设置）。
---

# MySQL 数据模型设计规范技能

## 概述

此技能旨在为 MySQL 数据库设计提供标准化的指导。它基于 [mysql_db_standard.md](references/mysql_db_standard.md) 中定义的行业及项目最佳实践，确保数据模型在可扩展性、性能和一致性方面达到高标准。

## 激活准则

当满足以下任意条件时，必须使用此技能：
1. **意图识别**：用户提到“设计数据模型”、“创建表”、“设计数据库”、“表结构”等。
2. **数据库识别**：明确提到使用 **MySQL**。

## 核心设计规范

在设计表结构时，必须强制遵守以下规则：

### 1. 基础字段规范
每个表必须包含且仅包含以下固定配置的审计字段：
- `version`: `int NOT NULL DEFAULT '0' COMMENT '版本'`
- `is_deleted`: `tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识'`
- `created_time`: `datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'`
- `created_by`: `bigint NOT NULL DEFAULT '0' COMMENT '创建人ID'`
- `modified_time`: `datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'`
- `modified_by`: `bigint NOT NULL DEFAULT '0' COMMENT '更新人ID'`

### 2. 参数与属性约束
- **主键 (id)**：每一张表必须拥有一个名为 `id` 的物理主键。类型统一为 `bigint NOT NULL AUTO_INCREMENT COMMENT '主键'`。这是索引优化和分库分表的基础。
- **NOT NULL 约束 (强制)**：**所有设置了默认值的字段必须定义为 `NOT NULL`**。
- **时间字段**：统一使用 `datetime(3)`，审计字段必须为 `NOT NULL` 并设置相应默认值。业务时间字段默认建议为 `NULL`（除非有明确业务默认逻辑）。
- **默认值设置**：
  - 除主键外，**业务字段必须有默认值**。
  - 数字型（int, bigint 等）默认 `0`。
  - 字符串型（varchar 等）默认 `''`。
  - 枚举型依具体业务逻辑设定默认值。
- **注释 (Comment)**：**所有字段**必须具备清晰的 `COMMENT` 说明。

### 3. DDL 示例
生成的 `CREATE TABLE` 语句应参考如下模版：
```sql
CREATE TABLE `table_name` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  -- 业务字段...
  `version` int NOT NULL DEFAULT '0' COMMENT '版本',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人ID',
  `modified_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `modified_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='业务含义说明';
```

## 执行流程

1. **需求分析**：理解业务实体及其关系。
2. **规范映射**：将业务字段按照“默认值强制设置”和“datetime(3) 统一”原则进行定义。
3. **审计注入**：自动补全 mandatory 的 6 个审计字段。
4. **DDL 生成**：输出符合 MySQL 规范的 SQL 及模型文档。

## 资源

### references/
- [mysql_db_standard.md](references/mysql_db_standard.md): 包含详细的建表示例及完整字段规则定义。
