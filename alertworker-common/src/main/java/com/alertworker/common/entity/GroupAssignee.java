package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_assignee")
public class GroupAssignee extends BaseEntity {
    private Long groupId;
    private Long assigneeId;
    private ChannelType channelType;
    private String serviceTypes;
}
