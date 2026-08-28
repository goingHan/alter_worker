package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.Assignee;
import com.alertworker.common.mapper.AssigneeMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assignees")
@RequiredArgsConstructor
public class AssigneeController {

    private final AssigneeMapper assigneeMapper;
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
        assigneeMapper.deleteById(id);
        auditLogService.log("DELETE", "ASSIGNEE", id, "admin", null);
    }
}
