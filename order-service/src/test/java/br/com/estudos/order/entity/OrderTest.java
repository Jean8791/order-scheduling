package br.com.estudos.order.entity;

import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.enums.OutboxEvent;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private Order newOrder() {
        return Order.create("teste@email.com", "Loja Exemplo",
                new Address("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE-500G", "Café 500g", 10)),
                LocalDate.of(2026, 12, 10), NOW);
    }

    @Test
    void newOrderStartsAwaitingScheduling() {
        Order order = newOrder();

        assertThat(order.getId()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_SCHEDULING);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
        assertThat(order.getScheduledDelivery()).isNull();
    }

    private Order orderWithKey(String owner, String key) {
        return Order.create(owner, "Loja Exemplo", new Address("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE-500G", "Café 500g", 10)), LocalDate.of(2026, 12, 10), NOW, key);
    }

    @Test
    void sameOwnerAndKeyGiveTheSameOrderId() {
        assertThat(orderWithKey("ana@email.com", "k1").getId()).isEqualTo(orderWithKey("ana@email.com", "k1").getId());
    }

    @Test
    void differentKeyOrDifferentOwnerGiveDifferentIds() {
        String id = orderWithKey("ana@email.com", "k1").getId();

        assertThat(orderWithKey("ana@email.com", "k2").getId()).isNotEqualTo(id);
        assertThat(orderWithKey("bia@email.com", "k1").getId()).isNotEqualTo(id);
    }

    @Test
    void withoutKeyEveryOrderGetsANewId() {
        assertThat(orderWithKey("ana@email.com", null).getId()).isNotEqualTo(orderWithKey("ana@email.com", null).getId());
    }

    @Test
    void sameRequestAsComparesTheContentTheClientSent() {
        Order order = newOrder();
        Order same = newOrder();
        Order other = Order.create("teste@email.com", "Outra Loja", order.getAddress(), order.getItems(),
                order.getDesiredDeliveryDate(), NOW);

        assertThat(order.sameRequestAs(same)).isTrue();
        assertThat(order.sameRequestAs(other)).isFalse();
    }

    @Test
    void newOrderHasTheCreatedEventPending() {
        assertThat(newOrder().getPendingEvents()).containsExactly(OutboxEvent.ORDER_CREATED);
    }

    @Test
    void cancelingAddsTheCanceledEventAfterTheCreatedOne() {
        Order order = newOrder();

        order.cancel("Cliente desistiu", LATER);

        assertThat(order.getPendingEvents()).containsExactly(OutboxEvent.ORDER_CREATED, OutboxEvent.ORDER_CANCELED);
    }

    @Test
    void confirmsSchedulingWhenAwaiting() {
        Order order = newOrder();
        ScheduledDelivery delivery = new ScheduledDelivery("slot-1", LocalDate.of(2026, 12, 10), "MORNING");

        assertThat(order.confirmScheduling(delivery, LATER)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(order.getScheduledDelivery()).isEqualTo(delivery);
        assertThat(order.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectedSchedulingKeepsReason() {
        Order order = newOrder();

        assertThat(order.rejectScheduling("Sem janela", LATER)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NOT_SCHEDULED);
        assertThat(order.getReason()).isEqualTo("Sem janela");
    }

    @Test
    void repeatedSchedulingEventIsIgnored() {
        Order order = newOrder();
        ScheduledDelivery first = new ScheduledDelivery("slot-1", LocalDate.of(2026, 12, 10), "MORNING");
        order.confirmScheduling(first, LATER);

        assertThat(order.confirmScheduling(new ScheduledDelivery("slot-2", LocalDate.of(2026, 12, 11), "AFTERNOON"), LATER)).isFalse();
        assertThat(order.rejectScheduling("too late", LATER)).isFalse();
        assertThat(order.getScheduledDelivery()).isEqualTo(first);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SCHEDULED);
    }

    @Test
    void confirmationArrivingAfterCancellationDoesNotReopenTheOrder() {
        Order order = newOrder();
        order.cancel("Cliente desistiu", LATER);

        assertThat(order.confirmScheduling(new ScheduledDelivery("slot-1", LocalDate.of(2026, 12, 10), "MORNING"), LATER)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cannotCancelTwice() {
        Order order = newOrder();
        order.cancel("Cliente desistiu", LATER);

        assertThatThrownBy(() -> order.cancel("de novo", LATER))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.UNPROCESSABLE)
                .hasMessageContaining("já está cancelado");
    }
}
