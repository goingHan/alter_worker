package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_rule_template")
public class AlertRuleTemplate {
    private Long id;
    private Long ruleId;
    private Long templateId;
    private ChannelType channelType;
    private LocalDateTime createdAt;
}
