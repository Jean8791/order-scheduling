package br.com.estudos.order.integration;

import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.enums.OutboxEvent;
import br.com.estudos.order.repository.OrderRepository;
import br.com.estudos.order.repository.OutboxRepository;
import br.com.estudos.order.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRepositoryIT extends IntegrationTest {

    @Autowired
    OutboxRepository outbox;

    @Autowired
    OrderRepository orders;

    private Order savedOrder() {
        return orders.save(Order.create("outbox@email.com", "Loja", new Address("Rua", "1", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE", "Café", 1)), LocalDate.of(2026, 12, 10), Instant.now()));
    }

    private List<String> pendingIds() {
        return outbox.findPending(1000).stream().map(Order::getId).toList();
    }

    @Test
    void clearedOrderIsNoLongerPending() {
        Order order = savedOrder();
        assertThat(pendingIds()).contains(order.getId());

        outbox.clear(outbox.findPending(1000).stream().filter(o -> o.getId().equals(order.getId())).toList());

        assertThat(pendingIds()).doesNotContain(order.getId());
    }

    @Test
    void eventAddedWhilePublishingStaysPending() {
        Order order = savedOrder();
        List<Order> readByTheRelay = outbox.findPending(1000).stream().filter(o -> o.getId().equals(order.getId())).toList();

        Order loaded = orders.findById(order.getId()).orElseThrow();
        loaded.cancel("Cliente desistiu", Instant.now());
        orders.save(loaded);

        outbox.clear(readByTheRelay);

        Order after = orders.findById(order.getId()).orElseThrow();
        assertThat(after.getPendingEvents()).containsExactly(OutboxEvent.ORDER_CREATED, OutboxEvent.ORDER_CANCELED);
        assertThat(pendingIds()).contains(order.getId());
    }
}
