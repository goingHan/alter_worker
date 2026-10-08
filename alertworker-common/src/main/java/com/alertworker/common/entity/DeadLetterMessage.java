package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("dead_letter_message")
public class DeadLetterMessage {
    private Long id;
    private Long alertMessageId;
    private String errorInfo;
    private LocalDateTime createdAt;
}
