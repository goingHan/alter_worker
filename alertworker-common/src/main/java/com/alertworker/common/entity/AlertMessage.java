package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.MessageSubStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_message")
public class AlertMessage extends BaseEntity {
    private String messageNo;
    private Long apiKeyId;
    private Long alertRuleId;
    private String payload;
    private String rawPayload;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: names of the channels actually resolved at ingest time
    private String channelNames;
    private MessageStatus status;
    private MessageSubStatus subStatus;
    private Integer retryCount;
    private LocalDateTime receivedAt;
    private LocalDateTime gwSentAt;
    private LocalDateTime workerReceivedAt;
    private LocalDateTime workerSentAt;
    private LocalDateTime finishedAt;
}
