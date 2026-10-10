package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_job")
public class ScheduledJob extends BaseEntity {
    private String jobType;
    private String name;
    private String cronExpr;
    private Boolean enabled;
    private String configJson;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
}
