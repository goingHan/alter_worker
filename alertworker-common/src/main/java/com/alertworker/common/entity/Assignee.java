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

    @NotBlank(message = "CN name is required")
    private String cnName;
}
