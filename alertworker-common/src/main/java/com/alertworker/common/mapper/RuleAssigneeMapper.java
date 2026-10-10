package com.alertworker.common.mapper;

import com.alertworker.common.dto.RuleAssigneeView;
import com.alertworker.common.entity.RuleAssignee;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

// #AI-CODE-FILE-dev_release/v0.0.1-20260902: direct rule->assignee binding mapper
@Mapper
public interface RuleAssigneeMapper extends BaseMapper<RuleAssignee> {

    @Select("SELECT ra.rule_id AS ruleId, ra.assignee_id AS assigneeId, ra.channel_type AS channelType, "
            + "a.cn_name AS cnName, a.phone AS phone, a.dingding_username AS dingdingUsername, a.email AS email "
            + "FROM rule_assignee ra "
            + "JOIN assignee a ON a.id = ra.assignee_id AND a.deleted = 0 AND a.enabled = TRUE "
            + "WHERE ra.rule_id = #{ruleId}")
    List<RuleAssigneeView> selectWithAssignee(@Param("ruleId") Long ruleId);
}
