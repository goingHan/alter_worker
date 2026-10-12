package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_report")
public class DailyReport extends BaseEntity {
    private String name;
    private String cronExpr;
    private Boolean enabled;
    private Long templateId;
    private Long scheduledJobId;
    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: optional statistics window within the run day (Asia/Shanghai),
    // minute granularity, inclusive at both ends. NULL on either side means the full day (00:00-24:00).
    // ALWAYS so an explicit null on update clears a previously configured bound.
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime statStartTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime statEndTime;
}
