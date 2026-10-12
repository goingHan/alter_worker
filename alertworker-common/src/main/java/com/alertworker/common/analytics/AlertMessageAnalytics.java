package com.alertworker.common.analytics;

import com.alertworker.common.dto.HourlyStatView;
import com.alertworker.common.dto.TimingStatRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dialect-specific analytics over {@code alert_message}. Implemented once per {@link com.alertworker.common.db.DatabaseVendor}
 * and selected by {@code @ConditionalOnDatabaseVendor}; everything that is expressible in portable SQL stays in the
 * shared MyBatis mappers.
 */
public interface AlertMessageAnalytics {

    /** Per-hour message counts (total / failed / silenced / unmatched / dead-letters) since {@code from}. */
    List<HourlyStatView> countHourlySince(LocalDateTime from);

    /** Latency statistics (avg / p95 / max per pipeline segment) over the window since {@code from}. */
    TimingStatRow timingStatsSince(LocalDateTime from);

    /**
     * Per-rule, per-hour alert counts of the {@code [start, end)} window. Rows are
     * {@code {ruleId, hourOfDay, cnt}}; hours without alerts are simply absent (caller fills zeros).
     */
    List<Map<String, Object>> countHourlyByRule(List<Long> ruleIds, LocalDateTime start, LocalDateTime end);
}
