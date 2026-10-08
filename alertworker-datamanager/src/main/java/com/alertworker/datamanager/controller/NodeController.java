package com.alertworker.datamanager.controller;

import com.alertworker.common.entity.NodeRegistry;
import com.alertworker.common.enums.NodeStatus;
import com.alertworker.common.mapper.NodeRegistryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class NodeController {

    private static final long HEARTBEAT_FRESH_MS = 30_000;

    private final NodeRegistryMapper nodeRegistryMapper;
    private final RestTemplate restTemplate = createRestTemplate();

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<NodeRegistry> nodes = nodeRegistryMapper.selectList(
                new LambdaQueryWrapper<NodeRegistry>().orderByAsc(NodeRegistry::getNodeType).orderByAsc(NodeRegistry::getId)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (NodeRegistry node : nodes) {
            result.add(toView(node));
        }
        return result;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<NodeRegistry> nodes = nodeRegistryMapper.selectList(new LambdaQueryWrapper<NodeRegistry>()
                .eq(NodeRegistry::getDeleted, 0));
        long gatewayTotal = 0, gatewayAvailable = 0, workerTotal = 0, workerAvailable = 0;
        for (NodeRegistry node : nodes) {
            boolean available = isAvailable(node);
            if ("GATEWAY".equals(node.getNodeType())) {
                gatewayTotal++;
                if (available) gatewayAvailable++;
            } else if ("WORKER".equals(node.getNodeType())) {
                workerTotal++;
                if (available) workerAvailable++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("gatewayTotal", gatewayTotal);
        result.put("gatewayAvailable", gatewayAvailable);
        result.put("workerTotal", workerTotal);
        result.put("workerAvailable", workerAvailable);
        return result;
    }

    @PutMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        NodeStatus nodeStatus;
        try {
            nodeStatus = NodeStatus.valueOf(status);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        NodeRegistry node = nodeRegistryMapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + id);
        }
        node.setStatus(nodeStatus.name());
        node.setUpdatedAt(LocalDateTime.now());
        nodeRegistryMapper.updateById(node);
        log.info("Node {} ({}) status updated to {}", node.getNodeId(), node.getNodeType(), nodeStatus);
        return toView(nodeRegistryMapper.selectById(id));
    }

    @PutMapping("/{id}/weight")
    public Map<String, Object> updateWeight(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object weightValue = body.get("weight");
        if (!(weightValue instanceof Number) || ((Number) weightValue).intValue() < 0) {
            throw new IllegalArgumentException("weight must be a non-negative number");
        }
        NodeRegistry node = nodeRegistryMapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + id);
        }
        node.setWeight(((Number) weightValue).intValue());
        node.setUpdatedAt(LocalDateTime.now());
        nodeRegistryMapper.updateById(node);
        log.info("Node {} weight updated to {}", node.getNodeId(), node.getWeight());
        return toView(nodeRegistryMapper.selectById(id));
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260908: remove stale registry entries; only offline nodes are deletable
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        NodeRegistry node = nodeRegistryMapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + id);
        }
        if (isAvailable(node)) {
            throw new IllegalArgumentException("Node " + node.getNodeId() + " is online and cannot be deleted");
        }
        nodeRegistryMapper.deleteById(id);
        log.info("Stale node {} ({}) removed from registry", node.getNodeId(), node.getNodeType());
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", true);
        result.put("nodeId", node.getNodeId());
        return result;
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id) {
        NodeRegistry node = nodeRegistryMapper.selectById(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        String nodeType = node.getNodeType() != null ? node.getNodeType() : "WORKER";
        // URL = 工程前缀 /pw/alertworker + 模块 context-path（gateway=/gw，worker=/worker，见各 application.yml）
        String path = "GATEWAY".equals(nodeType)
                ? "/pw/alertworker/gw/api/v1/gateway/test"
                : "/pw/alertworker/worker/api/v1/worker/test";
        String url = "http://" + node.getHost() + ":" + node.getPort() + path;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("url", url);
            result.put("reachable", true);
            result.put("statusCode", response.getStatusCode().value());
            result.put("detail", response.getBody());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Node self-test failed for {}: {}", node.getNodeId(), e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("url", url);
            result.put("reachable", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    private Map<String, Object> toView(NodeRegistry node) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", node.getId());
        view.put("nodeId", node.getNodeId());
        view.put("nodeType", node.getNodeType());
        view.put("host", node.getHost());
        view.put("port", node.getPort());
        view.put("status", node.getStatus());
        view.put("weight", node.getWeight());
        view.put("lastHeartbeatAt", node.getLastHeartbeatAt());
        view.put("available", isAvailable(node));
        return view;
    }

    private boolean isAvailable(NodeRegistry node) {
        if (node.getLastHeartbeatAt() == null) {
            return false;
        }
        return Duration.between(node.getLastHeartbeatAt(), LocalDateTime.now()).toMillis() < HEARTBEAT_FRESH_MS;
    }
}
