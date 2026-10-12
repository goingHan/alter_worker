package com.alertworker.datamanager.controller;

import com.alertworker.common.analytics.AlertMessageAnalytics;
import com.alertworker.common.dto.HourlyStatView;
import com.alertworker.common.dto.TimingStatRow;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.DeadLetterMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.DeadLetterMessage;
import com.alertworker.common.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final AlertMessageMapper alertMessageMapper;
    private final AlertMessageAnalytics alertMessageAnalytics;
    private final DeadLetterMessageMapper deadLetterMessageMapper;
    private final AlertRuleMapper alertRuleMapper;

    @GetMapping("/24h")
    public List<Map<String, Object>> last24Hours() {
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 24 buckets ending at the current hour so in-progress hours are not dropped
        LocalDateTime end = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime from = end.minusHours(23);
        List<HourlyStatView> rows = alertMessageAnalytics.countHourlySince(from);

        Map<String, HourlyStatView> byHour = new HashMap<>();
        for (HourlyStatView row : rows) {
            byHour.put(row.getHour().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")), row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
        for (int i = 0; i < 24; i++) {
            LocalDateTime hour = from.plusHours(i);
            String key = hour.format(f);
            HourlyStatView row = byHour.get(key);
            Map<String, Object> bucket = new HashMap<>();
            bucket.put("hour", hour.toString());
            bucket.put("total", row != null && row.getTotal() != null ? row.getTotal() : 0L);
            bucket.put("failed", row != null && row.getFailed() != null ? row.getFailed() : 0L);
            bucket.put("deadLetters", row != null && row.getDeadLetters() != null ? row.getDeadLetters() : 0L);
            bucket.put("silenced", row != null && row.getSilenced() != null ? row.getSilenced() : 0L);
            bucket.put("unmatched", row != null && row.getUnmatched() != null ? row.getUnmatched() : 0L);
            result.add(bucket);
        }
        return result;
    }

    @GetMapping("/timing")
    public Map<String, Object> timing() {
        LocalDateTime from = LocalDateTime.now().minusHours(24);
        TimingStatRow row = alertMessageAnalytics.timingStatsSince(from);
        if (row == null) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("gatewayProcessMs", segment(row.getGwAvg(), row.getGwP95(), row.getGwMax()));
        result.put("mqTransitMs", segment(row.getMqAvg(), row.getMqP95(), row.getMqMax()));
        result.put("workerProcessMs", segment(row.getWorkerAvg(), row.getWorkerP95(), row.getWorkerMax()));
        result.put("totalMs", segment(row.getTotalAvg(), row.getTotalP95(), row.getTotalMax()));
        result.put("count", row.getCount() != null ? row.getCount() : 0L);
        return result;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        LocalDateTime from = LocalDateTime.now().minusHours(24);
        long total = alertMessageMapper.selectCount(new LambdaQueryWrapper<AlertMessage>()
                .ge(AlertMessage::getReceivedAt, from));
        long failed = alertMessageMapper.selectCount(new LambdaQueryWrapper<AlertMessage>()
                .ge(AlertMessage::getReceivedAt, from)
                .eq(AlertMessage::getStatus, MessageStatus.FAILED));
        long success = alertMessageMapper.selectCount(new LambdaQueryWrapper<AlertMessage>()
                .ge(AlertMessage::getReceivedAt, from)
                .eq(AlertMessage::getStatus, MessageStatus.success));
        long deadLetters = deadLetterMessageMapper.selectCount(new LambdaQueryWrapper<DeadLetterMessage>()
                .ge(DeadLetterMessage::getCreatedAt, from));
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: silenced = dropped by a silenced rule in the last 24h
        long silenced = alertMessageMapper.selectCount(new LambdaQueryWrapper<AlertMessage>()
                .ge(AlertMessage::getReceivedAt, from)
                .eq(AlertMessage::getStatus, MessageStatus.SILENCED));
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: unmatched = dropped because no alert rule matched
        long unmatched = alertMessageMapper.selectCount(new LambdaQueryWrapper<AlertMessage>()
                .ge(AlertMessage::getReceivedAt, from)
                .eq(AlertMessage::getStatus, MessageStatus.UNMATCHED));
        long silencedRules = alertRuleMapper.selectCount(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getSilenced, true));
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("deadLetters", deadLetters);
        result.put("silenced", silenced);
        result.put("unmatched", unmatched);
        result.put("silencedRules", silencedRules);
        return result;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: alerts dropped at the gateway — by a silenced rule, or because no
    // rule matched. Newest first; the payload is trimmed here because the dashboard only renders a one-line summary.
    @GetMapping("/silenced")
    public List<Map<String, Object>> silenced(@RequestParam(defaultValue = "10") int size) {
        return droppedViews(MessageStatus.SILENCED, size);
    }

    @GetMapping("/unmatched")
    public List<Map<String, Object>> unmatched(@RequestParam(defaultValue = "10") int size) {
        return droppedViews(MessageStatus.UNMATCHED, size);
    }

    private List<Map<String, Object>> droppedViews(MessageStatus status, int size) {
        // the dashboard panel says "last 24h" and the matching card count uses the same window — apply it here too
        // so an old dropped alert can never linger in the list after it has aged out of the count
        LocalDateTime from = LocalDateTime.now().minusHours(24);
        List<Map<String, Object>> rows = alertMessageMapper.selectDroppedRecent(
                status.name(), from, Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> view = new HashMap<>(row);
            Object payload = row.get("payload");
            String text = payload == null ? "" : String.valueOf(payload);
            view.put("summary", text.length() > 200 ? text.substring(0, 200) + "..." : text);
            view.put("fullContent", text);
            view.remove("payload");
            result.add(view);
        }
        return result;
    }

    private Map<String, Object> segment(Double avg, Double p95, Double max) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("avg", round1(avg));
        seg.put("p95", round1(p95));
        seg.put("max", round1(max));
        return seg;
    }

    private Double round1(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
