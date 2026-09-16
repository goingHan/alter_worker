package com.alertworker.common.mapper;

import com.alertworker.common.dto.RuleGroupView;
import com.alertworker.common.dto.RuleGroupNameView;
import com.alertworker.common.entity.RuleGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RuleGroupMapper extends BaseMapper<RuleGroup> {

    @Select("SELECT rg.id AS id, rg.group_id AS groupId, cg.send_type AS sendType "
            + "FROM rule_group rg "
            + "JOIN communication_group cg ON cg.id = rg.group_id AND cg.deleted = 0 "
            + "WHERE rg.rule_id = #{ruleId}")
    List<RuleGroupView> selectWithGroupSendType(@Param("ruleId") Long ruleId);

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: bulk rule->group names for table column
    @Select("SELECT rg.rule_id AS ruleId, rg.group_id AS groupId, cg.name AS groupName "
            + "FROM rule_group rg "
            + "JOIN communication_group cg ON cg.id = rg.group_id AND cg.deleted = 0")
    List<RuleGroupNameView> selectAllWithGroupName();
}
