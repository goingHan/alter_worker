package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dictionary")
public class Dictionary extends BaseEntity {
    @NotBlank(message = "Dict type is required")
    private String dictType;
    @NotBlank(message = "Dict key is required")
    private String dictKey;
    @NotBlank(message = "Dict value is required")
    private String dictValue;
}
