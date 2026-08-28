package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.ApiKey;
import com.alertworker.common.mapper.ApiKeyMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyMapper apiKeyMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<ApiKey> list(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "20") int size) {
        return apiKeyMapper.selectPage(new Page<>(current, size), null);
    }

    @GetMapping("/{id}")
    public ApiKey get(@PathVariable Long id) {
        return apiKeyMapper.selectById(id);
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    @PostMapping
    public ApiKey create(@Valid @RequestBody ApiKey entity) {
        apiKeyMapper.insert(entity);
        auditLogService.log("CREATE", "API_KEY", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public ApiKey update(@PathVariable Long id, @Valid @RequestBody ApiKey entity) {
        entity.setId(id);
        apiKeyMapper.updateById(entity);
        auditLogService.log("UPDATE", "API_KEY", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        apiKeyMapper.deleteById(id);
        auditLogService.log("DELETE", "API_KEY", id, "admin", null);
    }
}
