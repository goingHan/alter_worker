package com.alertworker.common.entity;

import com.alertworker.common.enums.AlertType;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Alert type is required")
    private AlertType alertType;

    private String matchKey;
    private String matchValue;

    @Min(value = 1, message = "Retry max must be at least 1")
    @Max(value = 10, message = "Retry max must be at most 10")
    private Integer retryMax;
    private String retryBackoff;
}
