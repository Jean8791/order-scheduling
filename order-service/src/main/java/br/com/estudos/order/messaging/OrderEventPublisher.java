package br.com.estudos.order.messaging;

import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.enums.OutboxEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Publica no RabbitMQ, com o {@code RabbitTemplate}, os eventos pendentes dos pedidos (chamado pelo {@code OutboxRelay}).
 */
@Component
public class OrderEventPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 10_000;

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    /**
     * Publica todos os eventos pendentes dos pedidos e espera o RabbitMQ confirmar que os recebeu. Se alguma
     * confirmação falhar ou demorar, lança exceção e nada é limpo: o relay tenta de novo na próxima rodada.
     */
    public void publish(List<Order> orders) {
        rabbitTemplate.invoke(operations -> {
            for (Order order : orders) {
                for (OutboxEvent event : order.getPendingEvents()) {
                    switch (event) {
                        case ORDER_CREATED -> operations.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, created(order));
                        case ORDER_CANCELED -> operations.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CANCELED, canceled(order));
                    }
                }
            }
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    private Events.OrderCreated created(Order order) {
        int itemCount = order.getItems().stream().mapToInt(OrderItem::quantity).sum();
        return new Events.OrderCreated(UUID.randomUUID().toString(), clock.instant(), order.getId(),
                order.getAddress().zipCode(), order.getAddress().state(), order.getDesiredDeliveryDate(), itemCount);
    }

    private Events.OrderCanceled canceled(Order order) {
        return new Events.OrderCanceled(UUID.randomUUID().toString(), clock.instant(), order.getId(), order.getReason());
    }
}
