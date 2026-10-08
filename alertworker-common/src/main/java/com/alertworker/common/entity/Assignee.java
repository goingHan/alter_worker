package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assignee")
public class Assignee extends BaseEntity {
    private String phone;
    private String dingdingUsername;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: add email for direct email notification
    private String email;

    @NotBlank(message = "CN name is required")
    private String cnName;
}
