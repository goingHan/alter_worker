package com.alertworker.common.mapper;

import com.alertworker.common.dto.RuleChannelView;
import com.alertworker.common.entity.AlertRuleChannel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

// #AI-CODE-FILE-dev_release/v0.0.1-20260916: explicit rule->channel binding (a rule may bind several channels, all receive)
@Mapper
public interface AlertRuleChannelMapper extends BaseMapper<AlertRuleChannel> {

    @Select("SELECT rc.rule_id AS ruleId, rc.channel_id AS channelId, "
            + "mc.name AS channelName, mc.channel_type AS channelType "
            + "FROM alert_rule_channel rc "
            + "JOIN message_channel mc ON mc.id = rc.channel_id AND mc.deleted = 0 "
            + "WHERE rc.rule_id = #{ruleId} "
            + "ORDER BY mc.channel_type, mc.id")
    List<RuleChannelView> selectWithChannel(@Param("ruleId") Long ruleId);

    @Select("SELECT rc.rule_id AS ruleId, rc.channel_id AS channelId, "
            + "mc.name AS channelName, mc.channel_type AS channelType "
            + "FROM alert_rule_channel rc "
            + "JOIN message_channel mc ON mc.id = rc.channel_id AND mc.deleted = 0 "
            + "ORDER BY rc.rule_id, mc.channel_type, mc.id")
    List<RuleChannelView> selectAllWithChannel();
}
