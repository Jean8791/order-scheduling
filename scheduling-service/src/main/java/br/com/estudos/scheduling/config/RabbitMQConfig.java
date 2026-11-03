package br.com.estudos.scheduling.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static br.com.estudos.scheduling.messaging.Topology.*;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public Declarables topology() {
        TopicExchange orders = new TopicExchange(EXCHANGE_ORDERS, true, false);
        TopicExchange schedulings = new TopicExchange(EXCHANGE_SCHEDULINGS, true, false);
        DirectExchange dlx = new DirectExchange(EXCHANGE_DLX, true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(orders, schedulings, dlx));
        declarables.addAll(queueWithDlq(QUEUE_ORDER_CREATED, orders, RK_ORDER_CREATED, dlx));
        declarables.addAll(queueWithDlq(QUEUE_ORDER_CANCELED, orders, RK_ORDER_CANCELED, dlx));
        return new Declarables(declarables);
    }

    static List<Declarable> queueWithDlq(String name, TopicExchange source, String routingKey, DirectExchange dlx) {
        Queue queue = QueueBuilder.durable(name)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(name)
                .build();
        Queue dlq = QueueBuilder.durable(name + ".dlq").build();
        Binding binding = BindingBuilder.bind(queue).to(source).with(routingKey);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(name);
        return List.of(queue, dlq, binding, dlqBinding);
    }
}
