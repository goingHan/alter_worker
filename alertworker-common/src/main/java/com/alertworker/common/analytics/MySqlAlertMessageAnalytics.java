package com.alertworker.common.analytics;

import com.alertworker.common.db.ConditionalOnDatabaseVendor;
import com.alertworker.common.db.DatabaseVendor;
import com.alertworker.common.dto.HourlyStatView;
import com.alertworker.common.dto.TimingSample;
import com.alertworker.common.dto.TimingStatRow;
import com.alertworker.common.util.Percentiles;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MySQL analytics. Aggregations use portable/MySQL-native constructs
 * ({@code DATE_FORMAT}, {@code COUNT(DISTINCT CASE WHEN …)}, {@code TIMESTAMPDIFF}, {@code HOUR}).
 *
 * <p>MySQL has no {@code PERCENTILE_CONT}, so the timing query returns the per-message millisecond samples and
 * avg / p95 / max are computed in Java ({@link Percentiles}, linear interpolation — same semantics as PostgreSQL).
 */
@Component
@ConditionalOnDatabaseVendor(DatabaseVendor.MYSQL)
@RequiredArgsConstructor
public class MySqlAlertMessageAnalytics implements AlertMessageAnalytics {

    private static final String COUNT_HOURLY_SQL = """
            SELECT CAST(DATE_FORMAT(m.received_at, '%Y-%m-%d %H:00:00') AS DATETIME) AS `hour`,
                   COUNT(DISTINCT m.id) AS total,
                   COUNT(DISTINCT CASE WHEN m.status = 'FAILED' THEN m.id END) AS failed,
                   COUNT(DISTINCT CASE WHEN m.status = 'SILENCED' THEN m.id END) AS silenced,
                   COUNT(DISTINCT CASE WHEN m.status = 'UNMATCHED' THEN m.id END) AS unmatched,
                   COUNT(DISTINCT d.id) AS dead_letters
            FROM alert_message m
            LEFT JOIN dead_letter_message d ON d.alert_message_id = m.id
            WHERE m.received_at >= ? AND m.deleted = 0
            GROUP BY 1
            ORDER BY 1
            """;

    private static final String TIMING_SQL = """
            SELECT TIMESTAMPDIFF(MICROSECOND, received_at, gw_sent_at) / 1000.0 AS gw_ms,
                   TIMESTAMPDIFF(MICROSECOND, gw_sent_at, worker_received_at) / 1000.0 AS mq_ms,
                   TIMESTAMPDIFF(MICROSECOND, worker_received_at, worker_sent_at) / 1000.0 AS worker_ms,
                   TIMESTAMPDIFF(MICROSECOND, received_at, finished_at) / 1000.0 AS total_ms
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

    private static final RowMapper<TimingSample> SAMPLE_MAPPER = (rs, rowNum) -> {
        TimingSample sample = new TimingSample();
        sample.setGwMs(nullableDouble(rs, "gw_ms"));
        sample.setMqMs(nullableDouble(rs, "mq_ms"));
        sample.setWorkerMs(nullableDouble(rs, "worker_ms"));
        sample.setTotalMs(nullableDouble(rs, "total_ms"));
        return sample;
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<HourlyStatView> countHourlySince(LocalDateTime from) {
        return jdbcTemplate.query(COUNT_HOURLY_SQL, HOURLY_MAPPER, from);
    }

    @Override
    public TimingStatRow timingStatsSince(LocalDateTime from) {
        List<TimingSample> samples = jdbcTemplate.query(TIMING_SQL, SAMPLE_MAPPER, from);
        TimingStatRow row = new TimingStatRow();
        row.setCount((long) samples.size());

        Double[] gw = summarize(samples.stream().map(TimingSample::getGwMs).toList());
        row.setGwAvg(gw[0]);
        row.setGwP95(gw[1]);
        row.setGwMax(gw[2]);

        Double[] mq = summarize(samples.stream().map(TimingSample::getMqMs).toList());
        row.setMqAvg(mq[0]);
        row.setMqP95(mq[1]);
        row.setMqMax(mq[2]);

        Double[] worker = summarize(samples.stream().map(TimingSample::getWorkerMs).toList());
        row.setWorkerAvg(worker[0]);
        row.setWorkerP95(worker[1]);
        row.setWorkerMax(worker[2]);

        Double[] total = summarize(samples.stream().map(TimingSample::getTotalMs).toList());
        row.setTotalAvg(total[0]);
        row.setTotalP95(total[1]);
        row.setTotalMax(total[2]);
        return row;
    }

    @Override
    public List<Map<String, Object>> countHourlyByRule(List<Long> ruleIds, LocalDateTime start, LocalDateTime end) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ruleIds.size(), "?"));
        String sql = """
                SELECT alert_rule_id AS `ruleId`,
                       HOUR(created_at) AS `hourOfDay`,
                       COUNT(*) AS `cnt`
                FROM alert_message
                WHERE deleted = 0 AND message_type = 'ALERT' AND status <> 'SILENCED'
                  AND alert_rule_id IN (%s)
                  AND created_at >= ? AND created_at < ?
                GROUP BY alert_rule_id, HOUR(created_at)
                """.formatted(placeholders);
        Object[] args = new Object[ruleIds.size() + 2];
        for (int i = 0; i < ruleIds.size(); i++) {
            args[i] = ruleIds.get(i);
        }
        args[ruleIds.size()] = start;
        args[ruleIds.size() + 1] = end;
        return jdbcTemplate.queryForList(sql, args);
    }

    /** @return {@code [avg, p95, max]} over the non-null values, or all-null when the sample is empty. */
    private static Double[] summarize(List<Double> values) {
        List<Double> nonNull = values.stream().filter(Objects::nonNull).sorted().toList();
        if (nonNull.isEmpty()) {
            return new Double[]{null, null, null};
        }
        double avg = nonNull.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        double p95 = Percentiles.percentile(nonNull, 95);
        double max = nonNull.get(nonNull.size() - 1);
        return new Double[]{avg, p95, max};
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
