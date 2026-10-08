package com.alertworker.worker.service;

import com.alertworker.common.service.NodeHeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alertworker.node", name = "enabled", havingValue = "true")
public class WorkerListenerGuard {

    private final RabbitListenerEndpointRegistry registry;
    private final NodeHeartbeatService nodeHeartbeatService;

    @Scheduled(fixedRate = 10000, initialDelay = 15000)
    public void syncListenerState() {
        AbstractMessageListenerContainer container =
                (AbstractMessageListenerContainer) registry.getListenerContainer("alertMessageListener");
        if (container == null) {
            return;
        }
        boolean disabled = nodeHeartbeatService.isDisabled();
        boolean running = container.isRunning();
        if (disabled && running) {
            log.warn("Worker node {} disabled, pausing message listener", nodeHeartbeatService.getNodeId());
            container.stop();
        } else if (!disabled && !running) {
            log.info("Worker node {} enabled, resuming message listener", nodeHeartbeatService.getNodeId());
            container.start();
        }
    }
}
