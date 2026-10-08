package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_assignee")
public class GroupAssignee extends BaseEntity {
    private Long groupId;
    @NotNull(message = "Assignee id is required")
    private Long assigneeId;
    @NotNull(message = "Channel type is required")
    private ChannelType channelType;
    private String serviceTypes;
}
