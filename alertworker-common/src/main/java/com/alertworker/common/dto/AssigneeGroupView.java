package com.alertworker.common.dto;

import lombok.Data;

// #AI-CODE-FILE-dev_release/v0.0.1-20260902: assignee->group membership view
@Data
public class AssigneeGroupView {
    private Long groupId;
    private Long assigneeId;
    private String channelType;
    private String serviceTypes;
    private String groupName;
    private String sendType;
}
