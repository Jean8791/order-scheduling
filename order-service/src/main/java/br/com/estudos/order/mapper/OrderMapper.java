package br.com.estudos.order.mapper;

import br.com.estudos.order.dto.order.OrderCreateRequest;
import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.entity.ScheduledDelivery;
import br.com.estudos.order.messaging.Events;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Converte entre DTOs (o que trafega na API) e a entidade {@code Order}.
 */
@Component
public class OrderMapper {

    public Order toEntity(OrderCreateRequest req, String owner, Instant now) {
        List<OrderItem> items = req.items().stream().map(this::toItem).toList();
        return Order.create(owner, req.customer(), toAddress(req.address()), items, req.desiredDeliveryDate(), now);
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getCustomer(), order.getAddress(), order.getItems(),
                order.getDesiredDeliveryDate(), order.getStatus(), order.getScheduledDelivery(), order.getReason(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    public ScheduledDelivery toScheduledDelivery(Events.SchedulingConfirmed event) {
        return new ScheduledDelivery(event.slotId(), event.date(), event.period());
    }

    private Address toAddress(OrderCreateRequest.AddressRequest req) {
        return new Address(req.street(), req.number(), req.city(), req.state(), req.zipCode());
    }

    private OrderItem toItem(OrderCreateRequest.ItemRequest req) {
        return new OrderItem(req.sku(), req.description(), req.quantity());
    }
}
