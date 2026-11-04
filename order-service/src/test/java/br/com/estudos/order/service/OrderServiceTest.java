package br.com.estudos.order.service;

import br.com.estudos.order.dto.order.OrderCreateRequest;
import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import br.com.estudos.order.mapper.OrderMapper;
import br.com.estudos.order.messaging.Events;
import br.com.estudos.order.messaging.OrderEventPublisher;
import br.com.estudos.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final String OWNER = "ana@email.com";

    @Mock
    OrderRepository repository;

    @Mock
    OrderEventPublisher publisher;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, publisher, new OrderMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OrderCreateRequest request() {
        return new OrderCreateRequest("Loja Exemplo",
                new OrderCreateRequest.AddressRequest("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new OrderCreateRequest.ItemRequest("CAFE-500G", "Café 500g", 10)),
                LocalDate.of(2026, 12, 10));
    }

    private static Order awaitingOrder() {
        return Order.create(OWNER, "Loja Exemplo", new Address("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE-500G", "Café 500g", 10)), LocalDate.of(2026, 12, 10), NOW);
    }

    @Test
    void createSavesAndPublishesOrderCreated() {
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse order = service.create(request(), OWNER);

        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_SCHEDULING);
        assertThat(order.items()).containsExactly(new OrderItem("CAFE-500G", "Café 500g", 10));
        ArgumentCaptor<Order> published = ArgumentCaptor.forClass(Order.class);
        verify(publisher).orderCreated(published.capture());
        assertThat(published.getValue().getId()).isEqualTo(order.id());
    }

    @Test
    void findByIdOfMissingOrderThrowsNotFound() {
        when(repository.findByIdAndOwner("x", OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("x", OWNER))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND);
    }

    @Test
    void schedulingConfirmationUpdatesTheOrder() {
        Order order = awaitingOrder();
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        service.registerSchedulingConfirmed(new Events.SchedulingConfirmed("e1", NOW, order.getId(),
                "slot-1", LocalDate.of(2026, 12, 10), "MORNING"));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(saved.getValue().getScheduledDelivery().slotId()).isEqualTo("slot-1");
    }

    @Test
    void schedulingRejectionKeepsReason() {
        Order order = awaitingOrder();
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        service.registerSchedulingRejected(new Events.SchedulingRejected("e1", NOW, order.getId(), "Sem janela"));

        verify(repository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NOT_SCHEDULED);
        assertThat(order.getReason()).isEqualTo("Sem janela");
    }

    @Test
    void eventForCanceledOrderSavesNothing() {
        Order order = awaitingOrder();
        order.cancel("Cliente desistiu", NOW);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        service.registerSchedulingConfirmed(new Events.SchedulingConfirmed("e1", NOW, order.getId(),
                "slot-1", LocalDate.of(2026, 12, 10), "MORNING"));

        verify(repository, never()).save(any());
    }

    @Test
    void cancelSavesAndPublishesOrderCanceled() {
        Order order = awaitingOrder();
        when(repository.findByIdAndOwner(order.getId(), OWNER)).thenReturn(Optional.of(order));
        when(repository.save(order)).thenReturn(order);

        OrderResponse canceled = service.cancel(order.getId(), "Cliente desistiu", OWNER);

        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        verify(publisher).orderCanceled(order);
    }

    @Test
    void orderFromAnotherUserIsNotFound() {
        Order order = awaitingOrder();
        when(repository.findByIdAndOwner(order.getId(), "outra@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(order.getId(), "Tentativa", "outra@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido " + order.getId() + " não encontrado");
        verify(repository, never()).save(any());
    }
}
