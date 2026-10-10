package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Read/update endpoint for the periodic-test-message configuration, stored as a {@code scheduled_job} row with
 * {@code job_type=TEST_MESSAGE}. Configuration CRUD lives here (datamanager); the job is executed by the
 * data-processing module's scheduler. Manual triggering moved to the processor ({@code /pw/alertworker/dp}).
 *
 * <p>Unlike the daily-report cron, the test-job cron is only required to parse — it may legitimately fire
 * sub-minute. On every save the {@code next_run_at} is recomputed from the cron in {@code Asia/Shanghai} so the
 * processor's next tick picks up the new schedule.
 */
@RestController
@RequestMapping("/api/v1/scheduled-jobs")
@RequiredArgsConstructor
public class ScheduledJobController {

    private static final String DEFAULT_JOB_TYPE = "TEST_MESSAGE";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ScheduledJobMapper scheduledJobMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public ScheduledJob get(@RequestParam(defaultValue = DEFAULT_JOB_TYPE) String jobType) {
        return scheduledJobMapper.selectOne(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getJobType, jobType)
                .last("LIMIT 1"));
    }

    @PutMapping
    @Transactional
    public ScheduledJob upsert(@RequestBody ScheduledJobRequest request) {
        validateCron(request.getCronExpr());
        String jobType = request.getJobType() == null || request.getJobType().isBlank()
                ? DEFAULT_JOB_TYPE
                : request.getJobType();
        ScheduledJob job = scheduledJobMapper.selectOne(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getJobType, jobType)
                .last("LIMIT 1"));
        boolean created = job == null;
        if (job == null) {
            job = new ScheduledJob();
            job.setJobType(jobType);
        }
        job.setName(request.getName());
        job.setCronExpr(request.getCronExpr());
        job.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        job.setConfigJson(request.getConfigJson());
        job.setNextRunAt(nextRunInZone(job.getCronExpr()));
        if (created) {
            scheduledJobMapper.insert(job);
        } else {
            scheduledJobMapper.updateById(job);
        }
        auditLogService.log(created ? "CREATE" : "UPDATE", "SCHEDULED_JOB", job.getId(), "admin", null);
        return job;
    }

    /**
     * The test-message job may fire more often than once per day (even sub-minute), so only parseability is
     * enforced here. Empty/null is still invalid.
     */
    private static void validateCron(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new IllegalArgumentException("cronExpr is required");
        }
        try {
            CronExpression.parse(cronExpr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
        }
    }

    private static LocalDateTime nextRunInZone(String cronExpr) {
        ZonedDateTime next = CronExpression.parse(cronExpr).next(ZonedDateTime.now(ZONE));
        return next == null ? null : next.toLocalDateTime();
    }

    @Data
    public static class ScheduledJobRequest {
        private String jobType;
        private String name;
        private String cronExpr;
        private Boolean enabled;
        private String configJson;
    }
}
