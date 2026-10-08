package com.alertworker.common.service;

import com.alertworker.common.entity.AuditLog;
import com.alertworker.common.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuditLogService extends ServiceImpl<AuditLogMapper, AuditLog> {

    @Async("auditLogExecutor")
    public void log(String action, String entityType, Long entityId, String operator, String detail) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setOperator(operator);
            auditLog.setDetail(detail);
            auditLog.setCreatedAt(LocalDateTime.now());
            save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", detail, e);
        }
    }

    @Async("auditLogExecutor")
    public void logEvent(String action, String detail) {
        log(action, "SYSTEM", null, "SYSTEM", detail);
    }
}
