package com.alertworker.common.dto;

import lombok.Data;

// #AI-CODE-FILE-dev_release/v0.0.1-20260902: rule->assignee view with contact fields
@Data
public class RuleAssigneeView {
    private Long ruleId;
    private Long assigneeId;
    private String channelType;
    private String cnName;
    private String phone;
    private String dingdingUsername;
    private String email;
}
