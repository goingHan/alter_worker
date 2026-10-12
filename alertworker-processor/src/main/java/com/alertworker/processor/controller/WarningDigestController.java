package com.alertworker.processor.controller;

import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.alertworker.common.service.AuditLogService;
import com.alertworker.processor.scheduler.WarningDigestJobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual trigger for the WARNING digest: runs the aggregation once, synchronously, exactly as the scheduler tick
 * would, and reports WHETHER it sent anything (plus the reason when it did not) so the UI never shows a silent no-op
 * as success.
 */
@RestController
@RequestMapping("/api/v1/warning-digest")
@RequiredArgsConstructor
public class WarningDigestController {

    private final ScheduledJobMapper scheduledJobMapper;
    private final WarningDigestJobExecutor warningDigestJobExecutor;
    private final AuditLogService auditLogService;

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody(required = false) RunRequest request) {
        ScheduledJob job = scheduledJobMapper.selectOne(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getJobType, WarningDigestJobExecutor.JOB_TYPE)
                .last("LIMIT 1"));
        if (job == null) {
            job = new ScheduledJob();
            job.setJobType(WarningDigestJobExecutor.JOB_TYPE);
        }
        if (request != null && request.getConfigJson() != null && !request.getConfigJson().isBlank()) {
            job.setConfigJson(request.getConfigJson());
        }
        WarningDigestJobExecutor.DigestRunResult result = warningDigestJobExecutor.run(job);
        auditLogService.log("MANUAL_RUN", "WARNING_DIGEST", job.getId(), "admin",
                result.sent() ? "sent messageNo=" + result.messageNo() : "not sent: " + result.reason());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executed", true);
        body.put("sent", result.sent());
        body.put("reason", result.reason());
        body.put("alertCount", result.alertCount());
        body.put("recipientCount", result.recipientCount());
        body.put("messageNo", result.messageNo());
        return body;
    }

    @Data
    public static class RunRequest {
        private String configJson;
    }
}
