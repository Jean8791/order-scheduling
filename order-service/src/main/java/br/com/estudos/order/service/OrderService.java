package br.com.estudos.order.service;

import br.com.estudos.order.dto.order.OrderCreateRequest;
import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import br.com.estudos.order.mapper.OrderMapper;
import br.com.estudos.order.messaging.Events;
import br.com.estudos.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Regras dos pedidos.
 *
 * <p>Criar e cancelar só gravam o pedido; o evento correspondente vai junto, no mesmo documento, e o
 * {@code OutboxRelay} o publica depois (nenhum evento se perde se o RabbitMQ ou o serviço falharem).
 *
 * <p>Toda operação feita pela API recebe o dono (e-mail do token) e só enxerga
 * pedidos dele. Os métodos {@code registerScheduling*} são chamados pelos
 * eventos do RabbitMQ, não pela API, por isso buscam o pedido só pelo id.
 */
@Service
public class OrderService {

    private static final int MAX_KEY_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderService(OrderRepository repository, OrderMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Cria o pedido. Com {@code idempotencyKey}, repetir a chamada (por exemplo, depois de perder a resposta) devolve o
     * pedido que já foi criado em vez de criar outro; a mesma chave com conteúdo diferente é conflito.
     */
    public OrderResponse create(OrderCreateRequest request, String owner, String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "Idempotency-Key deve ter de 1 a " + MAX_KEY_LENGTH + " caracteres");
        }
        Order order = mapper.toEntity(request, owner, clock.instant(), idempotencyKey);
        try {
            Order saved = repository.save(order);
            log.info("Pedido {} criado, aguardando agendamento para {}", saved.getId(), saved.getDesiredDeliveryDate());
            return mapper.toResponse(saved);
        } catch (DuplicateKeyException repeated) {
            Order existing = repository.findByIdAndOwner(order.getId(), owner)
                    .orElseThrow(() -> BusinessException.of(ErrorCode.CONFLICT, "Idempotency-Key já usada"));
            if (!existing.sameRequestAs(order)) {
                throw BusinessException.of(ErrorCode.CONFLICT, "Idempotency-Key já usada em um pedido com conteúdo diferente");
            }
            log.info("Pedido {} repetido com a mesma Idempotency-Key, devolvendo o existente", existing.getId());
            return mapper.toResponse(existing);
        }
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
