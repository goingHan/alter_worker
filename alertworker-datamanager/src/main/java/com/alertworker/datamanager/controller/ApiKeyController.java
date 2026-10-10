package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.ApiKey;
import com.alertworker.common.mapper.ApiKeyMapper;
import com.alertworker.common.service.AuditLogService;
import com.alertworker.common.util.ApiKeyUtil;
import com.alertworker.common.util.SecretMaskUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyMapper apiKeyMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<ApiKey> list(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "20") int size) {
        Page<ApiKey> page = apiKeyMapper.selectPage(new Page<>(current, size), null);
        page.getRecords().forEach(r -> r.setApiValue(SecretMaskUtil.mask(r.getApiValue())));
        return page;
    }

    @GetMapping("/{id}")
    public ApiKey get(@PathVariable Long id) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        if (apiKey != null) {
            apiKey.setApiValue(SecretMaskUtil.mask(apiKey.getApiValue()));
        }
        return apiKey;
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261008: explicit reveal for the edit dialog, keeps list/get masked
    @GetMapping("/{id}/reveal")
    public ApiKey reveal(@PathVariable Long id) {
        ApiKey apiKey = apiKeyMapper.selectById(id);
        auditLogService.log("REVEAL", "API_KEY", id, "admin", null);
        return apiKey;
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: add @Valid for input validation
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260915: generate apiValue server-side so users never type the key
    @PostMapping
    public ApiKey create(@Valid @RequestBody ApiKey entity) {
        entity.setApiValue(ApiKeyUtil.generate());
        apiKeyMapper.insert(entity);
        auditLogService.log("CREATE", "API_KEY", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public ApiKey update(@PathVariable Long id, @Valid @RequestBody ApiKey entity) {
        if (entity.getApiValue() == null || entity.getApiValue().isBlank()
                || SecretMaskUtil.isMasked(entity.getApiValue())) {
            ApiKey existing = apiKeyMapper.selectById(id);
            if (existing != null) {
                entity.setApiValue(existing.getApiValue());
            }
        }
        entity.setId(id);
        apiKeyMapper.updateById(entity);
        auditLogService.log("UPDATE", "API_KEY", id, "admin", null);
        ApiKey updated = apiKeyMapper.selectById(id);
        if (updated != null) {
            updated.setApiValue(SecretMaskUtil.mask(updated.getApiValue()));
        }
        return updated;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        apiKeyMapper.deleteById(id);
        auditLogService.log("DELETE", "API_KEY", id, "admin", null);
    }
}
