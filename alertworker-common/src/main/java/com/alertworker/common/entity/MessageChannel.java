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
}
