package com.alertworker.common.mapper;

import com.alertworker.common.entity.AlertMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface AlertMessageMapper extends BaseMapper<AlertMessage> {

    @Select("""
            <script>
            SELECT m.id, m.message_no AS "messageNo", m.alert_rule_id AS "alertRuleId", m.status,
                   m.sub_status AS "subStatus", m.retry_count AS "retryCount",
                   m.received_at AS "receivedAt", m.created_at AS "createdAt", m.payload,
                   r.name AS "ruleName",
                   m.channel_names AS "channelNames",
                   EXISTS (SELECT 1 FROM dead_letter_message d WHERE d.alert_message_id = m.id) AS "inDeadLetter"
            FROM alert_message m
            LEFT JOIN alert_rule r ON r.id = m.alert_rule_id
            WHERE m.deleted = 0
            <if test="messageNo != null and messageNo != ''">
              AND m.message_no ILIKE CONCAT('%', #{messageNo}, '%')
            </if>
            <if test="status != null and status != ''">
              AND m.status = #{status}
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
              AND r.name ILIKE CONCAT('%', #{ruleName}, '%')
            </if>
            <if test="channelName != null and channelName != ''">
              AND m.channel_names ILIKE CONCAT('%', #{channelName}, '%')
            </if>
            ORDER BY m.created_at DESC
            </script>
            """)
    IPage<Map<String, Object>> searchHistory(Page<Map<String, Object>> page,
                                             @Param("messageNo") String messageNo,
                                             @Param("status") String status,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("inDeadLetter") Boolean inDeadLetter,
                                             @Param("ruleName") String ruleName,
                                             @Param("channelName") String channelName);
}
