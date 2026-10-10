package com.alertworker.processor.controller;

import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.alertworker.common.scheduler.JobExecutor;
import com.alertworker.common.service.AuditLogService;
import com.alertworker.processor.scheduler.TestMessageJobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Manual trigger for scheduled jobs (moved here from datamanager together with the executors).
 *
 * <p>The on-screen configuration sent in the request is used when present, so "立即执行一次" works before the job
 * has ever been saved; omitted fields fall back to the persisted row then to a sensible default.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobRunController {

    private final ScheduledJobMapper scheduledJobMapper;
    private final AuditLogService auditLogService;
    private final List<JobExecutor> jobExecutors;

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody RunRequest request) {
        String jobType = request.getJobType() == null || request.getJobType().isBlank()
                ? TestMessageJobExecutor.JOB_TYPE
                : request.getJobType();
        ScheduledJob persisted = scheduledJobMapper.selectOne(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getJobType, jobType)
                .last("LIMIT 1"));

        ScheduledJob job = new ScheduledJob();
        job.setId(persisted != null ? persisted.getId() : null);
        job.setJobType(jobType);
        job.setName(firstNonBlank(request.getName(), persisted != null ? persisted.getName() : jobType));
        job.setCronExpr(firstNonBlank(request.getCronExpr(),
                persisted != null ? persisted.getCronExpr() : "0 0 9 * * *"));
        job.setEnabled(true);
        job.setConfigJson(request.getConfigJson() != null
                ? request.getConfigJson()
                : (persisted != null ? persisted.getConfigJson() : null));

        JobExecutor executor = jobExecutors.stream()
                .filter(e -> e.jobType().equals(jobType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No executor for job type: " + jobType));
        executor.execute(job);
        auditLogService.log("MANUAL_RUN", "SCHEDULED_JOB", job.getId(), "admin", null);
        return Map.of("executed", true);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    @Data
    public static class RunRequest {
        private String jobType;
        private String name;
        private String cronExpr;
        private String configJson;
    }
}
