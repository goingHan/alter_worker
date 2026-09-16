package com.alertworker.common.entity;

import com.alertworker.common.enums.AlertType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Alert type is required")
    private AlertType alertType;

    @NotBlank(message = "Match key is required")
    private String matchKey;
    private String matchValue;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: ALWAYS so an explicit null on update clears stale conditions (deleted rows otherwise persist)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String matchConditions;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: legacy global AND/OR, used only when matchConditions has no per-condition "op"
    private String matchLogic;

    @Min(value = 1, message = "Retry max must be at least 1")
    @Max(value = 10, message = "Retry max must be at most 10")
    private Integer retryMax;
    private String retryBackoff;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: request-only field; null = leave bindings untouched,
    // empty list = unbind all (fall back to default channels)
    @TableField(exist = false)
    private List<Long> channelIds;
}
