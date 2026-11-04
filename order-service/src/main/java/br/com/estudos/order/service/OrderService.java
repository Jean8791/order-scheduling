package br.com.estudos.order.service;

import br.com.estudos.order.dto.order.OrderCreateRequest;
import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import br.com.estudos.order.mapper.OrderMapper;
import br.com.estudos.order.messaging.Events;
import br.com.estudos.order.messaging.OrderEventPublisher;
import br.com.estudos.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final OrderEventPublisher publisher;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderService(OrderRepository repository, OrderEventPublisher publisher, OrderMapper mapper, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
    }

    public OrderResponse create(OrderCreateRequest request, String owner) {
        Order order = repository.save(mapper.toEntity(request, owner, clock.instant()));
        publisher.orderCreated(order);
        log.info("Pedido {} criado, aguardando agendamento para {}", order.getId(), order.getDesiredDeliveryDate());
        return mapper.toResponse(order);
    }

    public OrderResponse findById(String id, String owner) {
        return mapper.toResponse(loadOwned(id, owner));
    }

    public Page<OrderResponse> list(OrderStatus status, String owner, Pageable pageable) {
        Page<Order> orders = status == null
                ? repository.findByOwner(owner, pageable)
                : repository.findByOwnerAndStatus(owner, status, pageable);
        return orders.map(mapper::toResponse);
    }

    public OrderResponse cancel(String id, String reason, String owner) {
        Order order = loadOwned(id, owner);
        order.cancel(reason, clock.instant());
        Order saved = repository.save(order);
        publisher.orderCanceled(saved);
        log.info("Pedido {} cancelado", id);
        return mapper.toResponse(saved);
    }

    public void registerSchedulingConfirmed(Events.SchedulingConfirmed event) {
        Order order = load(event.orderId());
        if (order.confirmScheduling(mapper.toScheduledDelivery(event), clock.instant())) {
            repository.save(order);
            log.info("Pedido {} agendado para {} ({})", order.getId(), event.date(), event.period());
        } else {
            log.info("Confirmação de agendamento ignorada: pedido {} está {}", order.getId(), order.getStatus());
        }
    }

    public void registerSchedulingRejected(Events.SchedulingRejected event) {
        Order order = load(event.orderId());
        if (order.rejectScheduling(event.reason(), clock.instant())) {
            repository.save(order);
            log.info("Pedido {} sem agendamento: {}", order.getId(), event.reason());
        } else {
            log.info("Rejeição de agendamento ignorada: pedido {} está {}", order.getId(), order.getStatus());
        }
    }

    private Order loadOwned(String id, String owner) {
        return repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "Pedido " + id + " não encontrado"));
    }

    private Order load(String id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "Pedido " + id + " não encontrado"));
    }
}
