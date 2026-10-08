package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

// #AI-CODE-FILE-dev_release/v0.0.1-20260902: direct rule->assignee binding entity
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rule_assignee")
public class RuleAssignee {
    private Long id;
    private Long ruleId;
    private Long assigneeId;
    private ChannelType channelType;
    private LocalDateTime createdAt;
}
