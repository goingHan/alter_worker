package com.alertworker.common.dto;

import lombok.Data;

@Data
public class RuleGroupView {
    private Long id;
    private Long groupId;
    private String sendType;
}
