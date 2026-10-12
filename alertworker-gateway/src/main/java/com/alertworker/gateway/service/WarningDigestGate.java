package com.alertworker.gateway.service;

import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gateway-side view of the warning-digest switch.
 *
 * <p>While the {@code WARNING_DIGEST} scheduled job exists and is enabled, WARNING-severity alerts are parked as
 * {@code WARNING_PENDING} at ingest instead of being delivered immediately. The switch lives on the scheduled-job row
 * (the single source of truth shared with the data-processing scheduler), and is read here with a short TTL cache so
 * a busy ingest path does not hit the database on every alert.
 */
@Component
@RequiredArgsConstructor
public class WarningDigestGate {

    public static final String JOB_TYPE = "WARNING_DIGEST";
    private static final long TTL_MS = 10_000L;

    private final ScheduledJobMapper scheduledJobMapper;

    private volatile boolean enabled = false;
    private volatile long checkedAt = 0L;

    public boolean enabled() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > TTL_MS) {
            ScheduledJob job = scheduledJobMapper.selectOne(new LambdaQueryWrapper<ScheduledJob>()
                    .eq(ScheduledJob::getJobType, JOB_TYPE)
                    .last("LIMIT 1"));
            enabled = job != null && Boolean.TRUE.equals(job.getEnabled());
            checkedAt = now;
        }
        return enabled;
    }
}
