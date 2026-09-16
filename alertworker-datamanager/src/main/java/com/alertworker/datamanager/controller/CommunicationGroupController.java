package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.CommunicationGroup;
import com.alertworker.common.entity.GroupAssignee;
import com.alertworker.common.entity.RuleGroup;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.CommunicationGroupMapper;
import com.alertworker.common.mapper.GroupAssigneeMapper;
import com.alertworker.common.mapper.RuleGroupMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class CommunicationGroupController {

    private final CommunicationGroupMapper communicationGroupMapper;
    private final GroupAssigneeMapper groupAssigneeMapper;
    private final RuleGroupMapper ruleGroupMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<CommunicationGroup> list(@RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "20") int size) {
        return communicationGroupMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public CommunicationGroup get(@PathVariable Long id) {
        return communicationGroupMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public CommunicationGroup create(@Valid @RequestBody CommunicationGroup entity) {
        communicationGroupMapper.insert(entity);
        auditLogService.log("CREATE", "COMMUNICATION_GROUP", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public CommunicationGroup update(@PathVariable Long id, @Valid @RequestBody CommunicationGroup entity) {
        entity.setId(id);
        communicationGroupMapper.updateById(entity);
        auditLogService.log("UPDATE", "COMMUNICATION_GROUP", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        groupAssigneeMapper.physicalDeleteByGroupId(id);
        ruleGroupMapper.delete(new LambdaQueryWrapper<RuleGroup>().eq(RuleGroup::getGroupId, id));
        communicationGroupMapper.deleteById(id);
        auditLogService.log("DELETE", "COMMUNICATION_GROUP", id, "admin", null);
    }

    @GetMapping("/{id}/assignees")
    public List<GroupAssignee> getAssignees(@PathVariable Long id) {
        return groupAssigneeMapper.selectList(
                new LambdaQueryWrapper<GroupAssignee>().eq(GroupAssignee::getGroupId, id)
        );
    }

    @PostMapping("/{id}/assignees")
    public void addAssignee(@PathVariable Long id, @Valid @RequestBody GroupAssignee entity) {
        entity.setGroupId(id);
        groupAssigneeMapper.insert(entity);
    }

    @DeleteMapping("/{id}/assignees/{assigneeId}")
    public void removeAssignee(@PathVariable Long id, @PathVariable Long assigneeId,
                               @RequestParam(required = false) ChannelType channelType) {
        if (channelType != null) {
            groupAssigneeMapper.physicalDeleteByGroupAssigneeChannel(id, assigneeId, channelType.name());
        } else {
            groupAssigneeMapper.physicalDeleteByGroupAndAssignee(id, assigneeId);
        }
    }
}
