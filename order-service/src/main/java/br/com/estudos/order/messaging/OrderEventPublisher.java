package br.com.estudos.order.messaging;

import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Publica os eventos de pedido no RabbitMQ com o {@code RabbitTemplate}.
 */
@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void orderCreated(Order order) {
        int itemCount = order.getItems().stream().mapToInt(OrderItem::quantity).sum();
        Events.OrderCreated event = new Events.OrderCreated(UUID.randomUUID().toString(), clock.instant(),
                order.getId(), order.getAddress().zipCode(), order.getAddress().state(),
                order.getDesiredDeliveryDate(), itemCount);
        rabbitTemplate.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, event);
    }

    public void orderCanceled(Order order) {
        Events.OrderCanceled event = new Events.OrderCanceled(UUID.randomUUID().toString(), clock.instant(),
                order.getId(), order.getReason());
        rabbitTemplate.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CANCELED, event);
    }
}
