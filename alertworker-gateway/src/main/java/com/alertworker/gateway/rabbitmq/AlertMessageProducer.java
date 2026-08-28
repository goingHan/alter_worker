package com.alertworker.gateway.rabbitmq;

import com.alertworker.common.dto.AlertMessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${alertworker.rabbitmq.exchange}")
    private String exchange;

    @Value("${alertworker.rabbitmq.routing-key}")
    private String routingKey;

    public void send(AlertMessagePayload payload) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            log.info("Sent alert message to RabbitMQ, alertRuleId={}", payload.getAlertRuleId());
        } catch (Exception e) {
            log.error("Failed to send alert message to RabbitMQ", e);
            throw new RuntimeException("RabbitMQ publish failed", e);
        }
    }
}
