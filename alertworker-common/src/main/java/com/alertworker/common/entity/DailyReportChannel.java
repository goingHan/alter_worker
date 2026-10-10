package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("daily_report_channel")
public class DailyReportChannel {
    private Long id;
    private Long reportId;
    private Long channelId;
    private LocalDateTime createdAt;
}
