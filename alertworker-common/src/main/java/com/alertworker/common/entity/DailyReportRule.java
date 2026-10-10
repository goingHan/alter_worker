package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("daily_report_rule")
public class DailyReportRule {
    private Long id;
    private Long reportId;
    private Long ruleId;
    private LocalDateTime createdAt;
}
