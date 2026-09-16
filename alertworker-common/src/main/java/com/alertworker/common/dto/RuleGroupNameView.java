package com.alertworker.common.dto;

import lombok.Data;

// #AI-CODE-FILE-dev_release/v0.0.1-20260902: rule->group name view for rule table display
@Data
public class RuleGroupNameView {
    private Long ruleId;
    private Long groupId;
    private String groupName;
}
