package com.alertworker.common.entity;

import com.alertworker.common.enums.ChannelType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("daily_report_recipient")
public class DailyReportRecipient {
    private Long id;
    private Long reportId;
    private Long assigneeId;
    private ChannelType channelType;
    private LocalDateTime createdAt;
}
