package com.alertworker.processor.controller;

import com.alertworker.common.entity.DailyReport;
import com.alertworker.common.mapper.DailyReportMapper;
import com.alertworker.common.service.AuditLogService;
import com.alertworker.processor.scheduler.DailyReportJobExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual trigger for a daily report (moved here from datamanager together with the executor): runs the aggregation
 * once, synchronously, exactly as the scheduler tick would, and returns WHETHER it sent anything (plus the reason when
 * it did not) so the UI never reports a silent no-op as success.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportRunController {

    private final DailyReportMapper dailyReportMapper;
    private final DailyReportJobExecutor dailyReportJobExecutor;
    private final AuditLogService auditLogService;

    @PostMapping("/{id}/run")
    public Map<String, Object> run(@PathVariable Long id) {
        DailyReport report = dailyReportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("Daily report not found: " + id);
        }
        if (!Boolean.TRUE.equals(report.getEnabled())) {
            throw new IllegalArgumentException("Daily report is disabled: " + id);
        }
        DailyReportJobExecutor.ReportRunResult result = dailyReportJobExecutor.runReport(report);
        auditLogService.log("MANUAL_RUN", "DAILY_REPORT", id, "admin",
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
}
