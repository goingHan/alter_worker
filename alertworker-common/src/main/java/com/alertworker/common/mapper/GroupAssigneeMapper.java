package com.alertworker.common.mapper;

import com.alertworker.common.dto.AssigneeGroupView;
import com.alertworker.common.dto.GroupAssigneeView;
import com.alertworker.common.entity.GroupAssignee;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupAssigneeMapper extends BaseMapper<GroupAssignee> {

    @Select("<script>"
            + "SELECT ga.group_id AS groupId, ga.assignee_id AS assigneeId, ga.channel_type AS channelType, "
            + "ga.service_types AS serviceTypes, a.cn_name AS cnName, a.phone AS phone, "
            + "a.dingding_username AS dingdingUsername, a.email AS email "
            + "FROM group_assignee ga "
            + "JOIN assignee a ON a.id = ga.assignee_id AND a.deleted = 0 "
            + "WHERE ga.deleted = 0 AND ga.group_id IN "
            + "<foreach collection='groupIds' item='gid' open='(' separator=',' close=')'>#{gid}</foreach>"
            + "</script>")
    List<GroupAssigneeView> selectWithAssignee(@Param("groupIds") List<Long> groupIds);

    @Delete("DELETE FROM group_assignee WHERE group_id = #{groupId} AND assignee_id = #{assigneeId}")
    int physicalDeleteByGroupAndAssignee(@Param("groupId") Long groupId, @Param("assigneeId") Long assigneeId);

    @Delete("DELETE FROM group_assignee WHERE group_id = #{groupId} AND assignee_id = #{assigneeId} "
            + "AND channel_type = #{channelType}")
    int physicalDeleteByGroupAssigneeChannel(@Param("groupId") Long groupId, @Param("assigneeId") Long assigneeId,
                                             @Param("channelType") String channelType);

    @Delete("DELETE FROM group_assignee WHERE group_id = #{groupId}")
    int physicalDeleteByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM group_assignee WHERE assignee_id = #{assigneeId}")
    int physicalDeleteByAssigneeId(@Param("assigneeId") Long assigneeId);

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: list groups for one assignee
    @Select("SELECT ga.group_id AS groupId, ga.assignee_id AS assigneeId, ga.channel_type AS channelType, "
            + "ga.service_types AS serviceTypes, cg.name AS groupName, cg.send_type AS sendType "
            + "FROM group_assignee ga "
            + "JOIN communication_group cg ON cg.id = ga.group_id AND cg.deleted = 0 "
            + "WHERE ga.deleted = 0 AND ga.assignee_id = #{assigneeId}")
    List<AssigneeGroupView> selectWithGroup(@Param("assigneeId") Long assigneeId);

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: list all assignee->group memberships
    @Select("SELECT ga.group_id AS groupId, ga.assignee_id AS assigneeId, ga.channel_type AS channelType, "
            + "ga.service_types AS serviceTypes, cg.name AS groupName, cg.send_type AS sendType "
            + "FROM group_assignee ga "
            + "JOIN communication_group cg ON cg.id = ga.group_id AND cg.deleted = 0 "
            + "WHERE ga.deleted = 0")
    List<AssigneeGroupView> selectAllGroups();
}
