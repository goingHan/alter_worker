package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("alert_rule_channel")
public class AlertRuleChannel {
    private Long id;
    private Long ruleId;
    private Long channelId;
    private LocalDateTime createdAt;
}
