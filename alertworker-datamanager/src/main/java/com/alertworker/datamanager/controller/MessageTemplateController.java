package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.AlertRuleTemplate;
import com.alertworker.common.entity.MessageTemplate;
import com.alertworker.common.mapper.AlertRuleTemplateMapper;
import com.alertworker.common.mapper.MessageTemplateMapper;
import com.alertworker.common.service.AuditLogService;
import com.alertworker.common.service.JinjavaTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateMapper messageTemplateMapper;
    private final AlertRuleTemplateMapper alertRuleTemplateMapper;
    private final AuditLogService auditLogService;
    private final JinjavaTemplateService jinjavaTemplateService;

    @GetMapping
    public Page<MessageTemplate> list(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "20") int size) {
        return messageTemplateMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public MessageTemplate get(@PathVariable Long id) {
        return messageTemplateMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public MessageTemplate create(@Valid @RequestBody MessageTemplate entity) {
        messageTemplateMapper.insert(entity);
        auditLogService.log("CREATE", "MESSAGE_TEMPLATE", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public MessageTemplate update(@PathVariable Long id, @Valid @RequestBody MessageTemplate entity) {
        entity.setId(id);
        messageTemplateMapper.updateById(entity);
        auditLogService.log("UPDATE", "MESSAGE_TEMPLATE", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: built-in templates are system-owned and may be edited but not deleted
        MessageTemplate existing = messageTemplateMapper.selectById(id);
        if (existing != null && Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("Built-in template cannot be deleted");
        }
        alertRuleTemplateMapper.delete(
                new LambdaQueryWrapper<AlertRuleTemplate>().eq(AlertRuleTemplate::getTemplateId, id));
        messageTemplateMapper.deleteById(id);
        auditLogService.log("DELETE", "MESSAGE_TEMPLATE", id, "admin", null);
    }

    @PostMapping("/{id}/preview")
    public String preview(@PathVariable Long id, @RequestBody Map<String, Object> context) {
        MessageTemplate template = messageTemplateMapper.selectById(id);
        if (template == null) {
            throw new IllegalArgumentException("Template not found");
        }
        return jinjavaTemplateService.render(template.getContent(), context);
    }
}
