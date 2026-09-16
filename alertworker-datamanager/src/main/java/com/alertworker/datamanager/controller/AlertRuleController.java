package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.AlertRule;
import com.alertworker.common.entity.AlertRuleChannel;
import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.RuleGroup;
import com.alertworker.common.entity.RuleAssignee;
import com.alertworker.common.dto.RuleChannelView;
import com.alertworker.common.dto.RuleGroupNameView;
import com.alertworker.common.enums.AlertType;
import com.alertworker.common.mapper.AlertRuleChannelMapper;
import com.alertworker.common.mapper.AlertRuleMapper;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.RuleGroupMapper;
import com.alertworker.common.mapper.RuleAssigneeMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleMapper alertRuleMapper;
    private final RuleGroupMapper ruleGroupMapper;
    private final RuleAssigneeMapper ruleAssigneeMapper;
    private final AlertRuleTemplateMapper alertRuleTemplateMapper;
    private final AlertRuleChannelMapper alertRuleChannelMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<AlertRule> list(@RequestParam(defaultValue = "1") int current,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) AlertType alertType) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(AlertRule::getName, name);
        }
        if (alertType != null) {
            wrapper.eq(AlertRule::getAlertType, alertType);
        }
        return alertRuleMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @GetMapping("/{id}")
    public AlertRule get(@PathVariable Long id) {
        return alertRuleMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: channelIds in the body replaces the rule's channel bindings
    // (null = untouched, empty list = unbind all -> default channels)
    @PostMapping
    @Transactional
    public AlertRule create(@Valid @RequestBody AlertRule entity) {
        alertRuleMapper.insert(entity);
        if (entity.getChannelIds() != null) {
            replaceChannels(entity.getId(), entity.getChannelIds());
        }
        auditLogService.log("CREATE", "ALERT_RULE", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    @Transactional
    public AlertRule update(@PathVariable Long id, @Valid @RequestBody AlertRule entity) {
        entity.setId(id);
        alertRuleMapper.updateById(entity);
        if (entity.getChannelIds() != null) {
            replaceChannels(id, entity.getChannelIds());
        }
        auditLogService.log("UPDATE", "ALERT_RULE", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ruleGroupMapper.delete(new LambdaQueryWrapper<RuleGroup>().eq(RuleGroup::getRuleId, id));
        ruleAssigneeMapper.delete(new LambdaQueryWrapper<RuleAssignee>().eq(RuleAssignee::getRuleId, id));
        alertRuleTemplateMapper.delete(
                new LambdaQueryWrapper<AlertRuleTemplate>().eq(AlertRuleTemplate::getRuleId, id));
        alertRuleChannelMapper.delete(
                new LambdaQueryWrapper<AlertRuleChannel>().eq(AlertRuleChannel::getRuleId, id));
        alertRuleMapper.deleteById(id);
        auditLogService.log("DELETE", "ALERT_RULE", id, "admin", null);
    }

    @GetMapping("/{id}/groups")
    public List<RuleGroup> getGroups(@PathVariable Long id) {
        return ruleGroupMapper.selectList(
                new LambdaQueryWrapper<RuleGroup>().eq(RuleGroup::getRuleId, id)
        );
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: bulk rule->group names for the rule table
    @GetMapping("/groups")
    public List<RuleGroupNameView> allGroups() {
        return ruleGroupMapper.selectAllWithGroupName();
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

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: explicit rule->channel bindings; every bound channel of a
    // recipient's channel type receives the alert (multiple same-type channels = fan-out)
    @GetMapping("/{id}/channels")
    public List<RuleChannelView> getChannels(@PathVariable Long id) {
        return alertRuleChannelMapper.selectWithChannel(id);
    }

    @GetMapping("/channels")
    public List<RuleChannelView> allChannels() {
        return alertRuleChannelMapper.selectAllWithChannel();
    }

    @PostMapping("/{id}/channels")
    public void addChannel(@PathVariable Long id, @RequestBody AlertRuleChannel entity) {
        if (entity.getChannelId() == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        Long exists = alertRuleChannelMapper.selectCount(
                new LambdaQueryWrapper<AlertRuleChannel>()
                        .eq(AlertRuleChannel::getRuleId, id)
                        .eq(AlertRuleChannel::getChannelId, entity.getChannelId()));
        if (exists != null && exists > 0) {
            return;
        }
        entity.setId(null);
        entity.setRuleId(id);
        alertRuleChannelMapper.insert(entity);
        auditLogService.log("UPDATE", "ALERT_RULE_CHANNEL", id, "admin", null);
    }

    @DeleteMapping("/{id}/channels/{channelId}")
    public void removeChannel(@PathVariable Long id, @PathVariable Long channelId) {
        alertRuleChannelMapper.delete(
                new LambdaQueryWrapper<AlertRuleChannel>()
                        .eq(AlertRuleChannel::getRuleId, id)
                        .eq(AlertRuleChannel::getChannelId, channelId));
        auditLogService.log("UPDATE", "ALERT_RULE_CHANNEL", id, "admin", null);
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: bulk replace channel bindings for several rules in one request
    @PutMapping("/channels")
    @Transactional
    public Map<String, Object> replaceChannelsBatch(@RequestBody RuleChannelBatchRequest request) {
        if (request.getRuleIds() == null || request.getRuleIds().isEmpty()) {
            throw new IllegalArgumentException("ruleIds is required");
        }
        int updated = 0;
        for (Long ruleId : request.getRuleIds()) {
            if (ruleId == null || alertRuleMapper.selectById(ruleId) == null) {
                continue;
            }
            replaceChannels(ruleId, request.getChannelIds());
            updated++;
        }
        auditLogService.log("UPDATE", "ALERT_RULE_CHANNEL", null, "admin",
                "bulk replace channels for " + updated + " rules");
        return Map.of("updated", updated);
    }

    private void replaceChannels(Long ruleId, List<Long> channelIds) {
        alertRuleChannelMapper.delete(
                new LambdaQueryWrapper<AlertRuleChannel>().eq(AlertRuleChannel::getRuleId, ruleId));
        if (channelIds == null || channelIds.isEmpty()) {
            return;
        }
        for (Long channelId : new LinkedHashSet<>(channelIds)) {
            if (channelId == null) {
                continue;
            }
            AlertRuleChannel binding = new AlertRuleChannel();
            binding.setRuleId(ruleId);
            binding.setChannelId(channelId);
            alertRuleChannelMapper.insert(binding);
        }
    }

    @Data
    public static class RuleChannelBatchRequest {
        private List<Long> ruleIds;
        private List<Long> channelIds;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: direct rule->assignee binding endpoints
    @GetMapping("/{id}/assignees")
    public List<RuleAssignee> getAssignees(@PathVariable Long id) {
        return ruleAssigneeMapper.selectList(
                new LambdaQueryWrapper<RuleAssignee>().eq(RuleAssignee::getRuleId, id)
        );
    }

    @PostMapping("/{id}/assignees")
    public void addAssignee(@PathVariable Long id, @RequestBody RuleAssignee entity) {
        entity.setRuleId(id);
        ruleAssigneeMapper.insert(entity);
    }

    @DeleteMapping("/{id}/assignees/{assigneeId}")
    public void removeAssignee(@PathVariable Long id, @PathVariable Long assigneeId) {
        ruleAssigneeMapper.delete(
                new LambdaQueryWrapper<RuleAssignee>()
                        .eq(RuleAssignee::getRuleId, id)
                        .eq(RuleAssignee::getAssigneeId, assigneeId)
        );
    }
}
