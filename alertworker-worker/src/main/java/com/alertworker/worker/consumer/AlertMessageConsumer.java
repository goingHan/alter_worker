package com.alertworker.worker.consumer;

import com.alertworker.common.dto.AlertMessagePayload;
import com.alertworker.common.service.NodeHeartbeatService;
import com.alertworker.worker.service.AlertMessageProcessor;
import com.alertworker.worker.service.ProcessingResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMessageConsumer {

    public static final String LISTENER_ID = "alertMessageListener";

    private final AlertMessageProcessor alertMessageProcessor;
    private final NodeHeartbeatService nodeHeartbeatService;

    @RabbitListener(id = LISTENER_ID, queues = "${alertworker.rabbitmq.queue}", ackMode = "MANUAL")
    public void onMessage(AlertMessagePayload payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long messageId = payload.getAlertMessageId();
        if (nodeHeartbeatService.isDisabled()) {
            log.warn("Worker node {} disabled, requeue message {}", nodeHeartbeatService.getNodeId(), messageId);
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        log.info("Received message from MQ, messageId={}, messageNo={}", messageId, payload.getMessageNo());
        ProcessingResult result = alertMessageProcessor.process(payload);
        if (result.success()) {
            channel.basicAck(deliveryTag, false);
        } else {
            log.warn("Message {} processing failed: {}, shouldRetry={}, retryDelayMs={}",
                    messageId, result.error(), result.shouldRetry(), result.retryDelayMs());
            if (result.shouldRetry()) {
                try {
                    Thread.sleep(result.retryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            channel.basicNack(deliveryTag, false, result.shouldRetry());
        }
    }
}
