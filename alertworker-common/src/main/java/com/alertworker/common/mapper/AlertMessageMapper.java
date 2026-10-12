package com.alertworker.common.mapper;

import com.alertworker.common.entity.AlertMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertMessageMapper extends BaseMapper<AlertMessage> {

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: dropped messages for the dashboard — the alert was accepted but
    // dropped before any channel (silenced rule, or no matching rule), so there is no delivery status or channel name
    // to show. rule_name is only present for the silenced case: an unmatched alert has no rule by definition.
    // The panel is labelled "last 24h" and the card counts use the same window, so the list is filtered on
    // received_at (the column every overview/24h stat keys on) to keep the rows and the count in agreement.
    // NOTE: double-quoted aliases are portable — PostgreSQL treats them as identifiers, MySQL accepts them as aliases.
    @Select("""
            SELECT m.id, m.message_no AS "messageNo", m.alert_rule_id AS "alertRuleId",
                   m.status, m.payload, m.received_at AS "receivedAt", m.created_at AS "createdAt",
                   r.name AS "ruleName"
            FROM alert_message m
            LEFT JOIN alert_rule r ON r.id = m.alert_rule_id
            WHERE m.deleted = 0 AND m.status = #{status} AND m.received_at >= #{from}
            ORDER BY m.created_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectDroppedRecent(@Param("status") String status,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("limit") int limit);

    // NOTE: LOWER(...) LIKE LOWER(...) is used instead of PostgreSQL's ILIKE so the query is portable across both
    // supported databases (MySQL's default utf8mb4 collations are case-insensitive anyway).
    @Select("""
            <script>
            SELECT m.id, m.message_no AS "messageNo", m.alert_rule_id AS "alertRuleId", m.status,
                   m.sub_status AS "subStatus", m.retry_count AS "retryCount",
                   m.received_at AS "receivedAt", m.created_at AS "createdAt", m.payload,
                   m.message_type AS "messageType",
                   r.name AS "ruleName",
                   r.severity AS "severity",
                   m.channel_names AS "channelNames",
                   EXISTS (SELECT 1 FROM dead_letter_message d WHERE d.alert_message_id = m.id) AS "inDeadLetter"
            FROM alert_message m
            LEFT JOIN alert_rule r ON r.id = m.alert_rule_id
            WHERE m.deleted = 0
            <if test="messageNo != null and messageNo != ''">
              AND LOWER(m.message_no) LIKE LOWER(CONCAT('%', #{messageNo}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND m.status = #{status}
            </if>
            <if test="severity != null and severity != ''">
              AND r.severity = #{severity}
            </if>
            <if test="start != null">
              AND m.created_at &gt;= #{start}
            </if>
            <if test="end != null">
              AND m.created_at &lt;= #{end}
            </if>
            <if test="inDeadLetter != null and inDeadLetter == true">
              AND EXISTS (SELECT 1 FROM dead_letter_message d WHERE d.alert_message_id = m.id)
            </if>
            <if test="inDeadLetter != null and inDeadLetter == false">
              AND NOT EXISTS (SELECT 1 FROM dead_letter_message d WHERE d.alert_message_id = m.id)
            </if>
            <if test="ruleName != null and ruleName != ''">
              AND LOWER(r.name) LIKE LOWER(CONCAT('%', #{ruleName}, '%'))
            </if>
            <if test="channelName != null and channelName != ''">
              AND LOWER(m.channel_names) LIKE LOWER(CONCAT('%', #{channelName}, '%'))
            </if>
            <if test="messageType != null and messageType != ''">
              AND m.message_type = #{messageType}
            </if>
            ORDER BY m.created_at DESC
            </script>
            """)
    IPage<Map<String, Object>> searchHistory(Page<Map<String, Object>> page,
                                             @Param("messageNo") String messageNo,
                                             @Param("status") String status,
                                             @Param("severity") String severity,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("inDeadLetter") Boolean inDeadLetter,
                                             @Param("ruleName") String ruleName,
                                             @Param("channelName") String channelName,
                                             @Param("messageType") String messageType);
}
