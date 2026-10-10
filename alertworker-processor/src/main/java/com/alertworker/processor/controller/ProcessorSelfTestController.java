package com.alertworker.processor.controller;

import com.alertworker.common.mapper.NodeRegistryMapper;
import com.alertworker.common.service.NodeHeartbeatService;
import com.alertworker.common.storage.ObjectStorageService;
import com.alertworker.common.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Processor node self-test, invoked by the datamanager node page's "测试" button.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processor")
@RequiredArgsConstructor
public class ProcessorSelfTestController {

    private final NodeHeartbeatService nodeHeartbeatService;
    private final NodeRegistryMapper nodeRegistryMapper;
    private final ConnectionFactory connectionFactory;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> selfTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeHeartbeatService.getNodeId());
        result.put("nodeType", "PROCESSOR");
        result.put("status", nodeHeartbeatService.isDisabled() ? "DISABLED" : "ENABLED");
        result.put("db", checkDb());
        result.put("mq", checkMq());
        result.put("storage", objectStorageService.getClass().getSimpleName());
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: surface the effective object-storage config so a
        // shadowing source (env / local default) is immediately visible when diagnosing 404 report links.
        Map<String, Object> storageConfig = new HashMap<>();
        storageConfig.put("type", storageProperties.getType());
        storageConfig.put("bucket", storageProperties.getBucket());
        storageConfig.put("home", storageProperties.getHome());
        storageConfig.put("publicBaseUrl", storageProperties.getPublicBaseUrl());
        result.put("storageConfig", storageConfig);
        boolean healthy = (boolean) result.get("db") && (boolean) result.get("mq");
        result.put("healthy", healthy);
        return healthy ? ResponseEntity.ok(result) : ResponseEntity.internalServerError().body(result);
    }

    private boolean checkDb() {
        try {
            return nodeRegistryMapper.selectCount(null) != null;
        } catch (Exception e) {
            log.warn("DB self-check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkMq() {
        try (Connection connection = connectionFactory.createConnection()) {
            return connection != null && connection.isOpen();
        } catch (Exception e) {
            log.warn("MQ self-check failed: {}", e.getMessage());
            return false;
        }
    }
}
