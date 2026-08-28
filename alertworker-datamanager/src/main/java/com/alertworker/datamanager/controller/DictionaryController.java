package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.Dictionary;
import com.alertworker.common.mapper.DictionaryMapper;
import com.alertworker.common.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryMapper dictionaryMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<Dictionary> list(@RequestParam(defaultValue = "1") int current,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String dictType) {
        LambdaQueryWrapper<Dictionary> wrapper = new LambdaQueryWrapper<>();
        if (dictType != null && !dictType.isEmpty()) {
            wrapper.eq(Dictionary::getDictType, dictType);
        }
        return dictionaryMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @GetMapping("/types/{dictType}")
    public List<Dictionary> getByType(@PathVariable String dictType) {
        return dictionaryMapper.selectList(
                new LambdaQueryWrapper<Dictionary>()
                        .eq(Dictionary::getDictType, dictType)
                        .orderByAsc(Dictionary::getDictKey)
        );
    }

    @PostMapping
    public Dictionary create(@RequestBody Dictionary entity) {
        dictionaryMapper.insert(entity);
        auditLogService.log("CREATE", "DICTIONARY", entity.getId(), "admin", null);
        return entity;
    }

    @PutMapping("/{id}")
    public Dictionary update(@PathVariable Long id, @RequestBody Dictionary entity) {
        entity.setId(id);
        dictionaryMapper.updateById(entity);
        auditLogService.log("UPDATE", "DICTIONARY", id, "admin", null);
        return entity;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        dictionaryMapper.deleteById(id);
        auditLogService.log("DELETE", "DICTIONARY", id, "admin", null);
    }
}
