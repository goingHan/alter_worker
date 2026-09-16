package com.alertworker.common.dto;

import lombok.Data;

@Data
public class GroupAssigneeView {
    private Long groupId;
    private Long assigneeId;
    private String channelType;
    private String serviceTypes;
    private String cnName;
    private String phone;
    private String dingdingUsername;
    private String email;
}
