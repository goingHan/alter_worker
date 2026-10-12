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
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: ALERT (normal ingest / test alert) | TEST (periodic channel test) | REPORT (daily report)
    private String messageType;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: for REPORT_PENDING rows, the id of the REPORT alert_message that aggregated this row
    private Long reportMessageId;
    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: for WARNING_PENDING rows, the id of the WARNING_DIGEST
    // alert_message that aggregated this row (separate from reportMessageId so the two pipelines never collide)
    private Long digestMessageId;
    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: for REPORT messages whose template is hosted HTML, the public
    // URL of the uploaded file and the object storage key it lives under
    private String contentUrl;
    private String objectKey;
    private Integer retryCount;
    private LocalDateTime receivedAt;
    private LocalDateTime gwSentAt;
    private LocalDateTime workerReceivedAt;
    private LocalDateTime workerSentAt;
    private LocalDateTime finishedAt;
}
