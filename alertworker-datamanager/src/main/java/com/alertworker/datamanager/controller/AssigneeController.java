package com.alertworker.datamanager.controller;

import com.alertworker.common.dto.AssigneeGroupView;
import com.alertworker.common.entity.Assignee;
import com.alertworker.common.entity.GroupAssignee;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.AssigneeMapper;
import com.alertworker.common.mapper.GroupAssigneeMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignees")
@RequiredArgsConstructor
public class AssigneeController {

    private final AssigneeMapper assigneeMapper;
    private final GroupAssigneeMapper groupAssigneeMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<Assignee> list(@RequestParam(defaultValue = "1") int current,
                               @RequestParam(defaultValue = "20") int size) {
        return assigneeMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public Assignee get(@PathVariable Long id) {
        return assigneeMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public Assignee create(@Valid @RequestBody Assignee entity) {
        assigneeMapper.insert(entity);
        auditLogService.log("CREATE", "ASSIGNEE", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public Assignee update(@PathVariable Long id, @Valid @RequestBody Assignee entity) {
        entity.setId(id);
        assigneeMapper.updateById(entity);
        auditLogService.log("UPDATE", "ASSIGNEE", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        groupAssigneeMapper.physicalDeleteByAssigneeId(id);
        assigneeMapper.deleteById(id);
        auditLogService.log("DELETE", "ASSIGNEE", id, "admin", null);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: dedicated enable/disable toggle. A disabled assignee stays
    // configured (group/rule bindings untouched) but is skipped during recipient resolution — no email, no DingDing @.
    // Kept separate from PUT /{id} so a plain edit never flips the switch by accident.
    @PutMapping("/{id}/status")
    public Assignee setStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        Assignee assignee = assigneeMapper.selectById(id);
        if (assignee == null || assignee.getDeleted() == 1) {
            throw new IllegalArgumentException("Assignee not found: " + id);
        }
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        Assignee update = new Assignee();
        update.setId(id);
        update.setEnabled(enabled);
        assigneeMapper.updateById(update);
        assignee.setEnabled(enabled);
        auditLogService.log(enabled ? "ASSIGNEE_ENABLE" : "ASSIGNEE_DISABLE", "ASSIGNEE", id, "admin",
                request.getReason());
        return assignee;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: assignee->group membership endpoints
    @GetMapping("/groups")
    public List<AssigneeGroupView> allGroups() {
        return groupAssigneeMapper.selectAllGroups();
    }

    @GetMapping("/{id}/groups")
    public List<AssigneeGroupView> getGroups(@PathVariable Long id) {
        return groupAssigneeMapper.selectWithGroup(id);
    }

    @PostMapping("/{id}/groups")
    public void addGroup(@PathVariable Long id, @RequestBody GroupAssignee entity) {
        entity.setAssigneeId(id);
        if (entity.getChannelType() == null) {
            entity.setChannelType(ChannelType.DINGDING);
        }
        groupAssigneeMapper.insert(entity);
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public void removeGroup(@PathVariable Long id, @PathVariable Long groupId,
                            @RequestParam(required = false) ChannelType channelType) {
        if (channelType != null) {
            groupAssigneeMapper.physicalDeleteByGroupAssigneeChannel(groupId, id, channelType.name());
        } else {
            groupAssigneeMapper.physicalDeleteByGroupAndAssignee(groupId, id);
        }
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: enable/disable toggle payload; reason is audit-only
    @Data
    public static class StatusRequest {
        private Boolean enabled;
        private String reason;
    }
}
