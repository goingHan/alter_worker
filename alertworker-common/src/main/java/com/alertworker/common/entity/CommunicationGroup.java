package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.SendType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("communication_group")
public class CommunicationGroup extends BaseEntity {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Send type is required")
    private SendType sendType;
}
