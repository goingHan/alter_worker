package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rule_group")
public class RuleGroup {
    private Long id;
    private Long ruleId;
    private Long groupId;
    private LocalDateTime createdAt;
}
