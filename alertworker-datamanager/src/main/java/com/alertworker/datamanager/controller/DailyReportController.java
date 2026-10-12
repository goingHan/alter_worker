package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.DailyReport;
import com.alertworker.common.entity.DailyReportChannel;
import com.alertworker.common.entity.DailyReportRecipient;
import com.alertworker.common.entity.DailyReportRule;
import com.alertworker.common.entity.DailyReportTrendRule;
import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.DailyReportChannelMapper;
import com.alertworker.common.mapper.DailyReportMapper;
import com.alertworker.common.mapper.DailyReportRecipientMapper;
import com.alertworker.common.mapper.DailyReportRuleMapper;
import com.alertworker.common.mapper.DailyReportTrendRuleMapper;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Daily-report configuration CRUD.
 *
 * <p>A report's schedule is mirrored 1:1 into a {@code scheduled_job} row ({@code job_type=DAILY_REPORT},
 * {@code config_json={"reportId": id}}) that the DataManager scheduler tick dispatches to
 * {@link DailyReportJobExecutor}. Creating/updating a report (re)creates/syncs that job and recomputes its
 * {@code next_run_at} in {@code Asia/Shanghai}; deleting a report removes the job. The cron only has to parse —
 * a report fires at every occurrence the cron defines (sub-daily frequency allowed), and each run reports its
 * rules' not-yet-reported alerts of that day.
 */
@RestController
@RequestMapping("/api/v1/daily-reports")
@RequiredArgsConstructor
public class DailyReportController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ENTITY_TYPE = "DAILY_REPORT";

    private final DailyReportMapper dailyReportMapper;
    private final DailyReportRuleMapper dailyReportRuleMapper;
    private final DailyReportRecipientMapper dailyReportRecipientMapper;
    private final DailyReportChannelMapper dailyReportChannelMapper;
    private final DailyReportTrendRuleMapper dailyReportTrendRuleMapper;
    private final ScheduledJobMapper scheduledJobMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Page<DailyReport> list(@RequestParam(defaultValue = "1") int current,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String name) {
        LambdaQueryWrapper<DailyReport> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(DailyReport::getName, name);
        }
        return dailyReportMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @GetMapping("/{id}")
    public DailyReport get(@PathVariable Long id) {
        return dailyReportMapper.selectById(id);
    }

    @PostMapping
    @Transactional
    public DailyReport create(@Valid @RequestBody DailyReport entity) {
        validateCron(entity.getCronExpr());
        validateStatWindow(entity);
        dailyReportMapper.insert(entity);
        ScheduledJob job = syncScheduledJob(entity);
        entity.setScheduledJobId(job.getId());
        dailyReportMapper.updateById(entity);
        auditLogService.log("CREATE", ENTITY_TYPE, entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    @Transactional
    public DailyReport update(@PathVariable Long id, @Valid @RequestBody DailyReport entity) {
        DailyReport existing = dailyReportMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Daily report not found: " + id);
        }
        validateCron(entity.getCronExpr());
        validateStatWindow(entity);
        entity.setId(id);
        if (entity.getScheduledJobId() == null) {
            entity.setScheduledJobId(existing.getScheduledJobId());
        }
        dailyReportMapper.updateById(entity);
        // Sync from the persisted row so the job always mirrors what is actually stored (e.g. a body that omitted
        // `enabled` must not silently disable the job).
        DailyReport saved = dailyReportMapper.selectById(id);
        ScheduledJob job = syncScheduledJob(saved);
        if (saved.getScheduledJobId() == null) {
            saved.setScheduledJobId(job.getId());
            dailyReportMapper.updateById(saved);
        }
        auditLogService.log("UPDATE", ENTITY_TYPE, id, "admin", null);
        return saved;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        DailyReport report = dailyReportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("Daily report not found: " + id);
        }
        dailyReportRuleMapper.delete(
                new LambdaQueryWrapper<DailyReportRule>().eq(DailyReportRule::getReportId, id));
        dailyReportRecipientMapper.delete(
                new LambdaQueryWrapper<DailyReportRecipient>().eq(DailyReportRecipient::getReportId, id));
        dailyReportChannelMapper.delete(
                new LambdaQueryWrapper<DailyReportChannel>().eq(DailyReportChannel::getReportId, id));
        dailyReportTrendRuleMapper.delete(
                new LambdaQueryWrapper<DailyReportTrendRule>().eq(DailyReportTrendRule::getReportId, id));
        if (report.getScheduledJobId() != null) {
            // Soft delete via @TableLogic on ScheduledJob (BaseEntity).
            scheduledJobMapper.deleteById(report.getScheduledJobId());
        }
        dailyReportMapper.deleteById(id);
        auditLogService.log("DELETE", ENTITY_TYPE, id, "admin", null);
    }

    @GetMapping("/{id}/rules")
    public List<DailyReportRule> getRules(@PathVariable Long id) {
        return dailyReportRuleMapper.selectList(
                new LambdaQueryWrapper<DailyReportRule>().eq(DailyReportRule::getReportId, id));
    }

    @PostMapping("/{id}/rules")
    public void addRule(@PathVariable Long id, @Valid @RequestBody DailyReportRule entity) {
        if (entity.getRuleId() == null) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (dailyReportRuleMapper.selectCount(new LambdaQueryWrapper<DailyReportRule>()
                .eq(DailyReportRule::getReportId, id)
                .eq(DailyReportRule::getRuleId, entity.getRuleId())) > 0) {
            return;
        }
        entity.setId(null);
        entity.setReportId(id);
        dailyReportRuleMapper.insert(entity);
        auditLogService.log("UPDATE", "DAILY_REPORT_RULE", id, "admin", null);
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    public void removeRule(@PathVariable Long id, @PathVariable Long ruleId) {
        dailyReportRuleMapper.delete(new LambdaQueryWrapper<DailyReportRule>()
                .eq(DailyReportRule::getReportId, id)
                .eq(DailyReportRule::getRuleId, ruleId));
        auditLogService.log("UPDATE", "DAILY_REPORT_RULE", id, "admin", null);
    }

    @GetMapping("/{id}/recipients")
    public List<DailyReportRecipient> getRecipients(@PathVariable Long id) {
        return dailyReportRecipientMapper.selectList(
                new LambdaQueryWrapper<DailyReportRecipient>().eq(DailyReportRecipient::getReportId, id));
    }

    @PostMapping("/{id}/recipients")
    public void addRecipient(@PathVariable Long id, @Valid @RequestBody DailyReportRecipient entity) {
        if (entity.getAssigneeId() == null) {
            throw new IllegalArgumentException("assigneeId is required");
        }
        if (entity.getChannelType() == null) {
            throw new IllegalArgumentException("channelType is required");
        }
        if (dailyReportRecipientMapper.selectCount(new LambdaQueryWrapper<DailyReportRecipient>()
                .eq(DailyReportRecipient::getReportId, id)
                .eq(DailyReportRecipient::getAssigneeId, entity.getAssigneeId())
                .eq(DailyReportRecipient::getChannelType, entity.getChannelType())) > 0) {
            return;
        }
        entity.setId(null);
        entity.setReportId(id);
        dailyReportRecipientMapper.insert(entity);
        auditLogService.log("UPDATE", "DAILY_REPORT_RECIPIENT", id, "admin", null);
    }

    @DeleteMapping("/{id}/recipients/{recipientId}")
    public void removeRecipient(@PathVariable Long id, @PathVariable Long recipientId) {
        dailyReportRecipientMapper.delete(new LambdaQueryWrapper<DailyReportRecipient>()
                .eq(DailyReportRecipient::getReportId, id)
                .eq(DailyReportRecipient::getId, recipientId));
        auditLogService.log("UPDATE", "DAILY_REPORT_RECIPIENT", id, "admin", null);
    }

    @GetMapping("/{id}/channels")
    public List<DailyReportChannel> getChannels(@PathVariable Long id) {
        return dailyReportChannelMapper.selectList(
                new LambdaQueryWrapper<DailyReportChannel>().eq(DailyReportChannel::getReportId, id));
    }

    @PostMapping("/{id}/channels")
    public void addChannel(@PathVariable Long id, @RequestBody DailyReportChannel entity) {
        if (entity.getChannelId() == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (dailyReportChannelMapper.selectCount(new LambdaQueryWrapper<DailyReportChannel>()
                .eq(DailyReportChannel::getReportId, id)
                .eq(DailyReportChannel::getChannelId, entity.getChannelId())) > 0) {
            return;
        }
        entity.setId(null);
        entity.setReportId(id);
        dailyReportChannelMapper.insert(entity);
        auditLogService.log("UPDATE", "DAILY_REPORT_CHANNEL", id, "admin", null);
    }

    @DeleteMapping("/{id}/channels/{channelId}")
    public void removeChannel(@PathVariable Long id, @PathVariable Long channelId) {
        dailyReportChannelMapper.delete(new LambdaQueryWrapper<DailyReportChannel>()
                .eq(DailyReportChannel::getReportId, id)
                .eq(DailyReportChannel::getChannelId, channelId));
        auditLogService.log("UPDATE", "DAILY_REPORT_CHANNEL", id, "admin", null);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: trend monitoring items are alert rules; for each selected rule
    // the report renders 平均/中位数/P20/P40/P60/P80/最大/最小 of the rule's per-hour alert counts of the run day.
    @GetMapping("/{id}/trend-rules")
    public List<DailyReportTrendRule> getTrendRules(@PathVariable Long id) {
        return dailyReportTrendRuleMapper.selectList(
                new LambdaQueryWrapper<DailyReportTrendRule>().eq(DailyReportTrendRule::getReportId, id));
    }

    @PostMapping("/{id}/trend-rules")
    public void addTrendRule(@PathVariable Long id, @RequestBody DailyReportTrendRule entity) {
        if (entity.getRuleId() == null) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (dailyReportTrendRuleMapper.selectCount(new LambdaQueryWrapper<DailyReportTrendRule>()
                .eq(DailyReportTrendRule::getReportId, id)
                .eq(DailyReportTrendRule::getRuleId, entity.getRuleId())) > 0) {
            return;
        }
        entity.setId(null);
        entity.setReportId(id);
        dailyReportTrendRuleMapper.insert(entity);
        auditLogService.log("UPDATE", "DAILY_REPORT_TREND_RULE", id, "admin", null);
    }

    @DeleteMapping("/{id}/trend-rules/{ruleId}")
    public void removeTrendRule(@PathVariable Long id, @PathVariable Long ruleId) {
        dailyReportTrendRuleMapper.delete(new LambdaQueryWrapper<DailyReportTrendRule>()
                .eq(DailyReportTrendRule::getReportId, id)
                .eq(DailyReportTrendRule::getRuleId, ruleId));
        auditLogService.log("UPDATE", "DAILY_REPORT_TREND_RULE", id, "admin", null);
    }

    /**
     * Validates a daily-report cron: it only has to parse. The reporter fires at every occurrence the cron
     * defines (sub-daily frequency is allowed); a blank or unparseable expression is rejected with a
     * {@link IllegalArgumentException} that the global exception handler maps to a 400.
     */
    private void validateCron(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new IllegalArgumentException("cronExpr is required");
        }
        try {
            CronExpression.parse(cronExpr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
        }
    }

    /**
     * The optional statistics window must be a valid range within the day: if both ends are set, the start must not
     * be after the end. Either side may be omitted (falls back to 00:00 / 24:00 in the executor).
     */
    private void validateStatWindow(DailyReport report) {
        if (report.getStatStartTime() != null && report.getStatEndTime() != null
                && report.getStatStartTime().isAfter(report.getStatEndTime())) {
            throw new IllegalArgumentException("统计起始时间不能晚于结束时间");
        }
    }

    /**
     * Creates (or updates) the DAILY_REPORT {@code scheduled_job} linked to the report: same name, cron and
     * enabled flag, {@code config_json = {"reportId": id}}, and {@code next_run_at} recomputed from the cron in
     * {@code Asia/Shanghai}. Returns the persisted job.
     */
    private ScheduledJob syncScheduledJob(DailyReport report) {
        ScheduledJob job = report.getScheduledJobId() != null
                ? scheduledJobMapper.selectById(report.getScheduledJobId())
                : null;
        if (job == null) {
            job = new ScheduledJob();
            job.setJobType("DAILY_REPORT");
        }
        job.setName(report.getName());
        job.setCronExpr(report.getCronExpr());
        job.setEnabled(Boolean.TRUE.equals(report.getEnabled()));
        job.setConfigJson(configJsonFor(report.getId()));
        job.setNextRunAt(nextRunInZone(job.getCronExpr()));
        if (job.getId() == null) {
            scheduledJobMapper.insert(job);
        } else {
            scheduledJobMapper.updateById(job);
        }
        return job;
    }

    private String configJsonFor(Long reportId) {
        try {
            return objectMapper.writeValueAsString(Map.of("reportId", reportId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize scheduled job config for report " + reportId, e);
        }
    }

    private LocalDateTime nextRunInZone(String cronExpr) {
        ZonedDateTime next = CronExpression.parse(cronExpr).next(ZonedDateTime.now(ZONE));
        return next == null ? null : next.toLocalDateTime();
    }
}
