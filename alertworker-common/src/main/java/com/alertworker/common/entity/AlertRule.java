package com.alertworker.common.entity;

import com.alertworker.common.enums.AlertSeverity;
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

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: the rule's own level (INFO/WARNING/CRITICAL), authoritative
    // over the severity carried by the incoming alert. NULL falls back to the DB default 'WARNING'.
    private AlertSeverity severity;

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

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: manual silence switch. While TRUE, every alert matched by this
    // rule (including test alerts) is dropped at the gateway: no channel resolution, no MQ message, only a SILENCED
    // alert_message record for the dashboard. NULL is normalised to FALSE by the DB default.
    private Boolean silenced;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: while TRUE, matching alerts are stored as REPORT_PENDING
    // (never enqueued) and aggregated by the daily-report job. Silence takes precedence over this flag.
    private Boolean reportOnly;

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: request-only field; null = leave bindings untouched,
    // empty list = unbind all (fall back to default channels)
    @TableField(exist = false)
    private List<Long> channelIds;
}
