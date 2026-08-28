package com.alertworker.common.mapper;

import com.alertworker.common.dto.GroupAssigneeView;
import com.alertworker.common.entity.GroupAssignee;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupAssigneeMapper extends BaseMapper<GroupAssignee> {

    @Select("<script>"
            + "SELECT ga.group_id AS groupId, ga.assignee_id AS assigneeId, ga.channel_type AS channelType, "
            + "ga.service_types AS serviceTypes, a.cn_name AS cnName, a.phone AS phone, "
            + "a.dingding_username AS dingdingUsername "
            + "FROM group_assignee ga "
            + "JOIN assignee a ON a.id = ga.assignee_id AND a.deleted = 0 "
            + "WHERE ga.deleted = 0 AND ga.group_id IN "
            + "<foreach collection='groupIds' item='gid' open='(' separator=',' close=')'>#{gid}</foreach>"
            + "</script>")
    List<GroupAssigneeView> selectWithAssignee(@Param("groupIds") List<Long> groupIds);
}
