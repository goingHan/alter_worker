package com.alertworker.processor.scheduler;

import com.alertworker.common.entity.ScheduledJob;
import com.alertworker.common.mapper.ScheduledJobMapper;
import com.alertworker.common.scheduler.JobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Data-processor scheduler tick. Runs every {@code alertworker.scheduler.tick-ms} (default 60s) and executes
 * due {@code scheduled_job} rows, dispatching each to the {@link JobExecutor} matching its {@code job_type}.
 *
 * <p>Single-instance guarantee: the tick acquires a PostgreSQL session-level advisory lock
 * ({@code pg_try_advisory_lock(hashtext('alertworker_scheduler')::bigint)}) on ONE dedicated connection and keeps
 * it open for the whole tick. A session-level lock dies with its session, so acquire + hold + release must happen
 * on the same connection — that is why the entire tick runs inside {@code JdbcTemplate.execute(ConnectionCallback)}
 * (the callback receives one connection that is only closed after the callback returns), instead of firing the
 * advisory-lock statement through the pooled {@code JdbcTemplate}. A second instance that cannot acquire the lock
 * returns immediately without running anything.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledJobService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LOCK_SQL =
            "SELECT pg_try_advisory_lock(hashtext('alertworker_scheduler')::bigint)";
    private static final String UNLOCK_SQL =
            "SELECT pg_advisory_unlock(hashtext('alertworker_scheduler')::bigint)";

    private final JdbcTemplate jdbcTemplate;
    private final ScheduledJobMapper scheduledJobMapper;
    private final List<JobExecutor> jobExecutors;

    @Scheduled(fixedDelayString = "${alertworker.scheduler.tick-ms:60000}")
    public void tick() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if (!tryAcquireLock(connection)) {
                log.debug("Scheduler tick skipped: advisory lock is held by another instance");
                return null;
            }
            log.debug("Scheduler tick: advisory lock acquired on dedicated connection");
            try {
                runDueJobs();
            } finally {
                releaseLock(connection);
            }
            return null;
        });
    }

    private void runDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledJob> dueJobs = scheduledJobMapper.selectList(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getEnabled, true)
                .isNotNull(ScheduledJob::getNextRunAt)
                .le(ScheduledJob::getNextRunAt, now));
        for (ScheduledJob job : dueJobs) {
            try {
                processDueJob(job, now);
            } catch (Exception e) {
                log.error("Skipping scheduled job id={} (type={}): failed to advance its schedule",
                        job.getId(), job.getJobType(), e);
            }
        }
        initializePendingJobs();
    }

    private void processDueJob(ScheduledJob job, LocalDateTime now) {
        JobExecutor executor = jobExecutors.stream()
                .filter(e -> e.jobType().equals(job.getJobType()))
                .findFirst()
                .orElse(null);
        if (executor == null) {
            log.warn("No JobExecutor registered for job_type={}, skipping job id={}",
                    job.getJobType(), job.getId());
            return;
        }
        boolean success = false;
        try {
            executor.execute(job);
            success = true;
        } catch (Exception e) {
            log.error("Scheduled job id={} (type={}) failed", job.getId(), job.getJobType(), e);
            // Fall through: next_run_at is still advanced below. A persistently failing job whose schedule is
            // NOT advanced would stay due forever and re-run on every single tick, starving the other jobs
            // and hammering a broken channel — advancing it keeps the failure visible but bounded to one
            // attempt per cron occurrence.
        }
        ScheduledJob update = new ScheduledJob();
        update.setId(job.getId());
        update.setNextRunAt(nextRunInZone(job.getCronExpr(), ZonedDateTime.now(ZONE)));
        if (success) {
            update.setLastRunAt(now);
        }
        scheduledJobMapper.updateById(update);
    }

    // Jobs created with next_run_at=NULL (e.g. seeded before this scheduler ever ran) are initialised to their
    // first cron instant after now, but NOT executed here — the first run happens on a later tick. last_run_at
    // is deliberately left untouched.
    private void initializePendingJobs() {
        List<ScheduledJob> pending = scheduledJobMapper.selectList(new LambdaQueryWrapper<ScheduledJob>()
                .eq(ScheduledJob::getEnabled, true)
                .isNull(ScheduledJob::getNextRunAt));
        for (ScheduledJob job : pending) {
            try {
                ScheduledJob update = new ScheduledJob();
                update.setId(job.getId());
                update.setNextRunAt(nextRunInZone(job.getCronExpr(), ZonedDateTime.now(ZONE)));
                scheduledJobMapper.updateById(update);
            } catch (Exception e) {
                log.error("Failed to initialise next_run_at for job id={}: {}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * Pure helper (package-visible for unit testing): the next instant matching {@code cronExpr} strictly after
     * {@code after}, evaluated in {@code after}'s zone and returned as a local wall-clock datetime.
     */
    static LocalDateTime nextRunInZone(String cronExpr, ZonedDateTime after) {
        CronExpression cron = CronExpression.parse(cronExpr);
        ZonedDateTime next = cron.next(after);
        return next == null ? null : next.toLocalDateTime();
    }

    private boolean tryAcquireLock(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOCK_SQL)) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            log.error("Failed to run pg_try_advisory_lock", e);
            return false;
        }
    }

    private void releaseLock(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(UNLOCK_SQL)) {
            rs.next();
        } catch (SQLException e) {
            log.error("Failed to run pg_advisory_unlock", e);
        }
    }
}
