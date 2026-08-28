package com.alertworker.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260805: exchange DTOs as JSON so the worker's listener can
        // deserialize AlertMessagePayload (Spring AMQP blocks Java-serialized unknown classes by default)
        return new Jackson2JsonMessageConverter(objectMapper, "com.alertworker.common.dto");
    }


    @Value("${alertworker.rabbitmq.exchange:alertworker.exchange}")
    private String exchange;

    @Value("${alertworker.rabbitmq.queue:alertworker.queue}")
    private String queue;

    @Value("${alertworker.rabbitmq.routing-key:alertworker.routing.key}")
    private String routingKey;

    @Value("${alertworker.rabbitmq.dlx:alertworker.dlx}")
    private String dlx;

    @Value("${alertworker.rabbitmq.dlq:alertworker.dlq}")
    private String dlq;

    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlx);
    }

    @Bean
    public Queue alertQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(dlq, true);
    }

    @Bean
    public Binding alertBinding() {
        return BindingBuilder.bind(alertQueue()).to(alertExchange()).with(routingKey);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(routingKey);
    }
}
