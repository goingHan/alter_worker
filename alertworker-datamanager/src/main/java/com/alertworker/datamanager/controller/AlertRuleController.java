package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.RuleGroup;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.RuleGroupMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleMapper alertRuleMapper;
    private final RuleGroupMapper ruleGroupMapper;
    private final AlertRuleTemplateMapper alertRuleTemplateMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<AlertRule> list(@RequestParam(defaultValue = "1") int current,
                                @RequestParam(defaultValue = "20") int size) {
        return alertRuleMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public AlertRule get(@PathVariable Long id) {
        return alertRuleMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public AlertRule create(@Valid @RequestBody AlertRule entity) {
        alertRuleMapper.insert(entity);
        auditLogService.log("CREATE", "ALERT_RULE", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public AlertRule update(@PathVariable Long id, @Valid @RequestBody AlertRule entity) {
        entity.setId(id);
        alertRuleMapper.updateById(entity);
        auditLogService.log("UPDATE", "ALERT_RULE", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        alertRuleMapper.deleteById(id);
        auditLogService.log("DELETE", "ALERT_RULE", id, "admin", null);
    }

    @GetMapping("/{id}/groups")
    public List<RuleGroup> getGroups(@PathVariable Long id) {
        return ruleGroupMapper.selectList(
                new LambdaQueryWrapper<RuleGroup>().eq(RuleGroup::getRuleId, id)
        );
    }

    @PostMapping("/{id}/groups")
    public void addGroup(@PathVariable Long id, @Valid @RequestBody RuleGroup entity) {
        entity.setRuleId(id);
        ruleGroupMapper.insert(entity);
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public void removeGroup(@PathVariable Long id, @PathVariable Long groupId) {
        ruleGroupMapper.delete(
                new LambdaQueryWrapper<RuleGroup>()
                        .eq(RuleGroup::getRuleId, id)
                        .eq(RuleGroup::getGroupId, groupId)
        );
    }

    @GetMapping("/{id}/templates")
    public List<AlertRuleTemplate> getTemplates(@PathVariable Long id) {
        return alertRuleTemplateMapper.selectList(
                new LambdaQueryWrapper<AlertRuleTemplate>().eq(AlertRuleTemplate::getRuleId, id)
        );
    }

    @PostMapping("/{id}/templates")
    public void addTemplate(@PathVariable Long id, @Valid @RequestBody AlertRuleTemplate entity) {
        entity.setRuleId(id);
        alertRuleTemplateMapper.insert(entity);
    }

    @DeleteMapping("/{id}/templates/{templateId}")
    public void removeTemplate(@PathVariable Long id, @PathVariable Long templateId) {
        alertRuleTemplateMapper.delete(
                new LambdaQueryWrapper<AlertRuleTemplate>()
                        .eq(AlertRuleTemplate::getRuleId, id)
                        .eq(AlertRuleTemplate::getTemplateId, templateId)
        );
    }
}
