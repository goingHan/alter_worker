package com.alertworker.common.analytics;

import com.alertworker.common.db.ConditionalOnDatabaseVendor;
import com.alertworker.common.db.DatabaseVendor;
import com.alertworker.common.dto.HourlyStatView;
import com.alertworker.common.dto.TimingStatRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL analytics: the aggregation queries can use native PostgreSQL features
 * ({@code date_trunc}, {@code FILTER}, {@code EXTRACT(EPOCH …)}, {@code PERCENTILE_CONT}).
 */
@Component
@ConditionalOnDatabaseVendor(DatabaseVendor.POSTGRESQL)
@RequiredArgsConstructor
public class PostgresAlertMessageAnalytics implements AlertMessageAnalytics {

    private static final String COUNT_HOURLY_SQL = """
            SELECT date_trunc('hour', m.received_at) AS hour,
                   COUNT(DISTINCT m.id) AS total,
                   COUNT(DISTINCT m.id) FILTER (WHERE m.status = 'FAILED') AS failed,
                   COUNT(DISTINCT m.id) FILTER (WHERE m.status = 'SILENCED') AS silenced,
                   COUNT(DISTINCT m.id) FILTER (WHERE m.status = 'UNMATCHED') AS unmatched,
                   COUNT(DISTINCT d.id) AS dead_letters
            FROM alert_message m
            LEFT JOIN dead_letter_message d ON d.alert_message_id = m.id
            WHERE m.received_at >= ? AND m.deleted = 0
            GROUP BY 1
            ORDER BY 1
            """;

    private static final String TIMING_SQL = """
            SELECT AVG(EXTRACT(EPOCH FROM (gw_sent_at - received_at)) * 1000)
                       FILTER (WHERE gw_sent_at IS NOT NULL) AS gw_avg,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (gw_sent_at - received_at)) * 1000)
                       FILTER (WHERE gw_sent_at IS NOT NULL) AS gw_p95,
                   MAX(EXTRACT(EPOCH FROM (gw_sent_at - received_at)) * 1000)
                       FILTER (WHERE gw_sent_at IS NOT NULL) AS gw_max,
                   AVG(EXTRACT(EPOCH FROM (worker_received_at - gw_sent_at)) * 1000)
                       FILTER (WHERE worker_received_at IS NOT NULL AND gw_sent_at IS NOT NULL) AS mq_avg,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (worker_received_at - gw_sent_at)) * 1000)
                       FILTER (WHERE worker_received_at IS NOT NULL AND gw_sent_at IS NOT NULL) AS mq_p95,
                   MAX(EXTRACT(EPOCH FROM (worker_received_at - gw_sent_at)) * 1000)
                       FILTER (WHERE worker_received_at IS NOT NULL AND gw_sent_at IS NOT NULL) AS mq_max,
                   AVG(EXTRACT(EPOCH FROM (worker_sent_at - worker_received_at)) * 1000)
                       FILTER (WHERE worker_sent_at IS NOT NULL AND worker_received_at IS NOT NULL) AS worker_avg,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (worker_sent_at - worker_received_at)) * 1000)
                       FILTER (WHERE worker_sent_at IS NOT NULL AND worker_received_at IS NOT NULL) AS worker_p95,
                   MAX(EXTRACT(EPOCH FROM (worker_sent_at - worker_received_at)) * 1000)
                       FILTER (WHERE worker_sent_at IS NOT NULL AND worker_received_at IS NOT NULL) AS worker_max,
                   AVG(EXTRACT(EPOCH FROM (finished_at - received_at)) * 1000)
                       FILTER (WHERE finished_at IS NOT NULL) AS total_avg,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (finished_at - received_at)) * 1000)
                       FILTER (WHERE finished_at IS NOT NULL) AS total_p95,
                   MAX(EXTRACT(EPOCH FROM (finished_at - received_at)) * 1000)
                       FILTER (WHERE finished_at IS NOT NULL) AS total_max,
                   COUNT(*) AS count
            FROM alert_message
            WHERE received_at >= ? AND deleted = 0
            """;

    private static final RowMapper<HourlyStatView> HOURLY_MAPPER = (rs, rowNum) -> {
        HourlyStatView view = new HourlyStatView();
        java.sql.Timestamp hour = rs.getTimestamp("hour");
        view.setHour(hour == null ? null : hour.toLocalDateTime());
        view.setTotal(rs.getLong("total"));
        view.setFailed(rs.getLong("failed"));
        view.setSilenced(rs.getLong("silenced"));
        view.setUnmatched(rs.getLong("unmatched"));
        view.setDeadLetters(rs.getLong("dead_letters"));
        return view;
    };

    private static final RowMapper<TimingStatRow> TIMING_MAPPER = (rs, rowNum) -> {
        TimingStatRow row = new TimingStatRow();
        row.setGwAvg(nullableDouble(rs, "gw_avg"));
        row.setGwP95(nullableDouble(rs, "gw_p95"));
        row.setGwMax(nullableDouble(rs, "gw_max"));
        row.setMqAvg(nullableDouble(rs, "mq_avg"));
        row.setMqP95(nullableDouble(rs, "mq_p95"));
        row.setMqMax(nullableDouble(rs, "mq_max"));
        row.setWorkerAvg(nullableDouble(rs, "worker_avg"));
        row.setWorkerP95(nullableDouble(rs, "worker_p95"));
        row.setWorkerMax(nullableDouble(rs, "worker_max"));
        row.setTotalAvg(nullableDouble(rs, "total_avg"));
        row.setTotalP95(nullableDouble(rs, "total_p95"));
        row.setTotalMax(nullableDouble(rs, "total_max"));
        row.setCount(rs.getLong("count"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<HourlyStatView> countHourlySince(LocalDateTime from) {
        return jdbcTemplate.query(COUNT_HOURLY_SQL, HOURLY_MAPPER, from);
    }

    @Override
    public TimingStatRow timingStatsSince(LocalDateTime from) {
        List<TimingStatRow> rows = jdbcTemplate.query(TIMING_SQL, TIMING_MAPPER, from);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> countHourlyByRule(List<Long> ruleIds, LocalDateTime start, LocalDateTime end) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ruleIds.size(), "?"));
        String sql = """
                SELECT alert_rule_id AS "ruleId",
                       CAST(EXTRACT(HOUR FROM created_at) AS int) AS "hourOfDay",
                       COUNT(*) AS "cnt"
                FROM alert_message
                WHERE deleted = 0 AND message_type = 'ALERT' AND status <> 'SILENCED'
                  AND alert_rule_id IN (%s)
                  AND created_at >= ? AND created_at < ?
                GROUP BY alert_rule_id, EXTRACT(HOUR FROM created_at)
                """.formatted(placeholders);
        Object[] args = new Object[ruleIds.size() + 2];
        for (int i = 0; i < ruleIds.size(); i++) {
            args[i] = ruleIds.get(i);
        }
        args[ruleIds.size()] = start;
        args[ruleIds.size() + 1] = end;
        return jdbcTemplate.queryForList(sql, args);
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
