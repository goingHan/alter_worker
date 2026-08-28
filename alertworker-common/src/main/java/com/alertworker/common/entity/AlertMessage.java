package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alertworker.common.enums.MessageStatus;
import com.alertworker.common.enums.MessageSubStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_message")
public class AlertMessage extends BaseEntity {
    private Long apiKeyId;
    private Long alertRuleId;
    private String payload;
    private MessageStatus status;
    private MessageSubStatus subStatus;
    private Integer retryCount;
}
