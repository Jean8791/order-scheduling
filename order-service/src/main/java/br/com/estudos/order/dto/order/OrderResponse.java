package br.com.estudos.order.dto.order;

import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.entity.ScheduledDelivery;
import br.com.estudos.order.enums.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrderResponse(
        String id,
        String customer,
        Address address,
        List<OrderItem> items,
        LocalDate desiredDeliveryDate,
        OrderStatus status,
        ScheduledDelivery scheduledDelivery,
        String reason,
        Instant createdAt,
        Instant updatedAt) {
}
