package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_channel")
public class MessageChannel extends BaseEntity {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Channel type is required")
    private ChannelType channelType;

    private String configJson;

    private Long defaultTemplateId;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: per-type default channel, used when a rule binds no channel explicitly
    private Boolean isDefault;
}
