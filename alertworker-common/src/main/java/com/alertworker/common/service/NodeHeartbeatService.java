package com.alertworker.common.service;

import com.alertworker.common.enums.NodeType;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alertworker.node", name = "enabled", havingValue = "true")
public class NodeHeartbeatService {

    private final NodeRegistryService nodeRegistryService;

    @Value("${alertworker.node.type}")
    private String nodeType;

    @Value("${server.port}")
    private int port;

    @Value("${alertworker.node.host:}")
    private String hostOverride;

    @Getter
    private String nodeId;

    @PostConstruct
    public void init() {
        nodeId = resolveHost() + ":" + port;
        register();
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void heartbeat() {
        register();
    }

    public boolean isDisabled() {
        return nodeRegistryService.isDisabled(nodeId);
    }

    private void register() {
        try {
            nodeRegistryService.register(nodeId, NodeType.valueOf(nodeType), resolveHost(), port);
        } catch (Exception e) {
            log.warn("Failed to register/heartbeat node {}: {}", nodeId, e.getMessage());
        }
    }

    private String resolveHost() {
        if (hostOverride != null && !hostOverride.isBlank()) {
            return hostOverride;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
