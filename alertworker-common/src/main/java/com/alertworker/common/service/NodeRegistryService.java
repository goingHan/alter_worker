package com.alertworker.common.service;

import com.alertworker.common.entity.NodeRegistry;
import com.alertworker.common.enums.NodeStatus;
import com.alertworker.common.enums.NodeType;
import com.alertworker.common.mapper.NodeRegistryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRegistryService {

    private final NodeRegistryMapper nodeRegistryMapper;

    public NodeRegistry getByNodeId(String nodeId) {
        return nodeRegistryMapper.selectOne(
                new LambdaQueryWrapper<NodeRegistry>().eq(NodeRegistry::getNodeId, nodeId)
        );
    }

    public void register(String nodeId, NodeType type, String host, int port) {
        NodeRegistry existing = getByNodeId(nodeId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            NodeRegistry node = new NodeRegistry();
            node.setNodeId(nodeId);
            node.setNodeType(type.name());
            node.setHost(host);
            node.setPort(port);
            node.setStatus(NodeStatus.ENABLED.name());
            node.setWeight(1);
            node.setLastHeartbeatAt(now);
            nodeRegistryMapper.insert(node);
            log.info("Registered node {} ({}:{})", nodeId, host, port);
        } else {
            nodeRegistryMapper.update(null, new LambdaUpdateWrapper<NodeRegistry>()
                    .eq(NodeRegistry::getNodeId, nodeId)
                    .set(NodeRegistry::getNodeType, type.name())
                    .set(NodeRegistry::getHost, host)
                    .set(NodeRegistry::getPort, port)
                    .set(NodeRegistry::getLastHeartbeatAt, now)
                    .set(NodeRegistry::getUpdatedAt, now));
        }
    }

    public void heartbeat(String nodeId) {
        nodeRegistryMapper.update(null, new LambdaUpdateWrapper<NodeRegistry>()
                .eq(NodeRegistry::getNodeId, nodeId)
                .set(NodeRegistry::getLastHeartbeatAt, LocalDateTime.now())
                .set(NodeRegistry::getUpdatedAt, LocalDateTime.now()));
    }

    public boolean isDisabled(String nodeId) {
        NodeRegistry node = getByNodeId(nodeId);
        return node != null && NodeStatus.DISABLED.name().equals(node.getStatus());
    }
}
