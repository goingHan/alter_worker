package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_report")
public class DailyReport extends BaseEntity {
    private String name;
    private String cronExpr;
    private Boolean enabled;
    private Long templateId;
    private Long scheduledJobId;
}
