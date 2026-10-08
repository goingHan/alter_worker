package com.alertworker.worker.controller;

import com.alertworker.common.service.NodeHeartbeatService;
import com.alertworker.common.mapper.NodeRegistryMapper;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/worker")
@RequiredArgsConstructor
public class WorkerSelfTestController {

    private final NodeHeartbeatService nodeHeartbeatService;
    private final NodeRegistryMapper nodeRegistryMapper;
    private final ConnectionFactory connectionFactory;

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> selfTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeHeartbeatService.getNodeId());
        result.put("nodeType", "WORKER");
        result.put("status", nodeHeartbeatService.isDisabled() ? "DISABLED" : "ENABLED");
        result.put("db", checkDb());
        result.put("mq", checkMq());
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
