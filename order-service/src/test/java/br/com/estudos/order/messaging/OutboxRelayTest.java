package br.com.estudos.order.messaging;

import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    OutboxRepository outbox;

    @Mock
    OrderEventPublisher publisher;

    private static Order order() {
        return Order.create("ana@email.com", "Loja", new Address("Rua", "1", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE", "Café", 1)), LocalDate.of(2026, 12, 10), Instant.parse("2026-11-03T12:00:00Z"));
    }

    @Test
    void publishesThePendingEventsAndOnlyThenClearsThem() {
        List<Order> pending = List.of(order(), order());
        when(outbox.findPending(2)).thenReturn(pending).thenReturn(List.of());
        OutboxRelay relay = new OutboxRelay(outbox, publisher, 2);

        relay.relay();

        InOrder inOrder = inOrder(publisher, outbox);
        inOrder.verify(publisher).publish(pending);
        inOrder.verify(outbox).clear(pending);
    }

    @Test
    void keepsTheEventsPendingWhenPublishingFails() {
        List<Order> pending = List.of(order());
        when(outbox.findPending(10)).thenReturn(pending);
        doThrow(new IllegalStateException("RabbitMQ fora do ar")).when(publisher).publish(pending);
        OutboxRelay relay = new OutboxRelay(outbox, publisher, 10);

        assertThatThrownBy(relay::relay).isInstanceOf(IllegalStateException.class);

        verify(outbox, never()).clear(anyList());
    }

    @Test
    void doesNothingWhenNothingIsPending() {
        when(outbox.findPending(10)).thenReturn(List.of());

        new OutboxRelay(outbox, publisher, 10).relay();

        verifyNoInteractions(publisher);
        verify(outbox, never()).clear(anyList());
    }

    @Test
    void keepsDrainingWhileBatchesComeFull() {
        List<Order> first = List.of(order(), order());
        List<Order> second = List.of(order());
        when(outbox.findPending(2)).thenReturn(first).thenReturn(second);
        OutboxRelay relay = new OutboxRelay(outbox, publisher, 2);

        relay.relay();

        verify(publisher).publish(first);
        verify(publisher).publish(second);
        verify(outbox).clear(first);
        verify(outbox).clear(second);
        assertThat(second).hasSize(1);
    }
}
