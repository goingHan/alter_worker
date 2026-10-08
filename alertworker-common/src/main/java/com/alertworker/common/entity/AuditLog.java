package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("audit_log")
public class AuditLog {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String operator;
    private String detail;
    private LocalDateTime createdAt;
}
