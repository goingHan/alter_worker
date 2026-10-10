package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.enums.TemplateFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_template")
public class MessageTemplate extends BaseEntity {
    @NotBlank(message = "Name is required")
    private String name;

    private ChannelType channelType;

    @NotBlank(message = "Content is required")
    private String content;

    private Boolean isDefault;

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: TEXT (inline) or HTML (rendered to a file uploaded to object storage)
    private TemplateFormat format;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: TRUE for seeded built-in templates (e.g. channel test templates), read-only in the UI
    private Boolean builtin;
}
