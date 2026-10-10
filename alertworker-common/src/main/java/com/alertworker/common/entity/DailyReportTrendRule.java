package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * A rule selected as a daily-report trend monitoring item. For each selected rule the report computes
 * 平均 / 中位数 / P20 / P40 / P60 / P80 / 最大 / 最小 of the rule's per-hour alert counts over the run day.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("daily_report_trend_rule")
public class DailyReportTrendRule {
    private Long id;
    private Long reportId;
    private Long ruleId;
    private LocalDateTime createdAt;
}
