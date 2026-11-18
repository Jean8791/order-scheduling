package br.com.estudos.scheduling.service;

import br.com.estudos.scheduling.config.SchedulingProperties;
import br.com.estudos.scheduling.dto.scheduling.SchedulingResponse;
import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.entity.Scheduling;
import br.com.estudos.scheduling.enums.SchedulingStatus;
import br.com.estudos.scheduling.mapper.SchedulingMapper;
import br.com.estudos.scheduling.messaging.Events;
import br.com.estudos.scheduling.messaging.SchedulingEventPublisher;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import br.com.estudos.scheduling.repository.SchedulingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Regras do agendamento, disparadas pelos eventos de pedido.
 *
 * <p>Os eventos podem chegar repetidos (o RabbitMQ entrega "pelo menos uma
 * vez"), então o processamento é idempotente: se o pedido já tem agendamento,
 * reenvia o resultado em vez de reservar outra vaga.
 */
@Service
public class SchedulingService {

    static final String NO_SLOT_REASON = "Sem janela de entrega disponível até %s para a UF %s";

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final SchedulingRepository schedulings;
    private final DeliverySlotRepository slots;
    private final SchedulingEventPublisher publisher;
    private final SchedulingMapper mapper;
    private final SchedulingProperties properties;
    private final Clock clock;

    public SchedulingService(SchedulingRepository schedulings, DeliverySlotRepository slots,
                             SchedulingEventPublisher publisher, SchedulingMapper mapper, SchedulingProperties properties,
                             Clock clock) {
        this.schedulings = schedulings;
        this.slots = slots;
        this.publisher = publisher;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<SchedulingResponse> findByOrderId(String orderId) {
        return schedulings.findById(orderId).map(mapper::toResponse);
    }

    public void schedule(Events.OrderCreated event) {
        Optional<DeliverySlot> booked = bookFirstAvailableSlot(event);
        Instant now = clock.instant();
        Scheduling scheduling = booked
                .map(slot -> Scheduling.confirmed(event.orderId(), slot, now))
                .orElseGet(() -> Scheduling.rejected(event.orderId(), String.format(NO_SLOT_REASON, searchEnd(event), event.state()), now));

        try {
            schedulings.insert(scheduling);
        } catch (DuplicateKeyException alreadyDecided) {
            booked.ifPresent(slot -> slots.release(slot.getId()));
            resendDecision(event.orderId());
            return;
        } catch (RuntimeException e) {
            booked.ifPresent(slot -> releaseKeepingError(slot, e));
            throw e;
        }

        if (booked.isPresent()) {
            publisher.confirmed(scheduling);
            log.info("Pedido {} agendado na janela {} ({} {})", scheduling.getOrderId(), scheduling.getSlotId(), scheduling.getDate(), scheduling.getPeriod());
        } else {
            publisher.rejected(scheduling);
            log.info("Pedido {} sem janela disponível", scheduling.getOrderId());
        }
    }

    public void cancel(Events.OrderCanceled event) {
        Optional<Scheduling> existing = schedulings.findById(event.orderId());

        if (existing.isEmpty()) {
            try {
                schedulings.insert(Scheduling.canceledBeforeProcessing(event.orderId(), event.reason(), clock.instant()));
                log.info("Pedido {} cancelado antes do agendamento", event.orderId());
                return;
            } catch (DuplicateKeyException race) {
                existing = schedulings.findById(event.orderId());
            }
        }

        Scheduling scheduling = existing.orElseThrow();
        if (scheduling.getStatus() == SchedulingStatus.CANCELED) {
            return;
        }
        if (scheduling.holdsSlot()) {
            slots.release(scheduling.getSlotId());
        }
        scheduling.cancel(event.reason(), clock.instant());
        schedulings.save(scheduling);
        log.info("Agendamento do pedido {} cancelado", event.orderId());
    }

    private Optional<DeliverySlot> bookFirstAvailableSlot(Events.OrderCreated event) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        LocalDate from = event.desiredDeliveryDate().isBefore(today) ? today : event.desiredDeliveryDate();
        LocalDate to = searchEnd(event);
        if (to.isBefore(from)) {
            return Optional.empty();
        }
        return slots.withAvailability(event.state(), from, to).stream()
                .map(candidate -> slots.book(candidate.getId()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private LocalDate searchEnd(Events.OrderCreated event) {
        return event.desiredDeliveryDate().plusDays(properties.searchDays());
    }

    /** Evento repetido ou cancelamento que chegou no meio: vale a decisão que já está gravada. */
    private void resendDecision(String orderId) {
        Scheduling decided = schedulings.findById(orderId).orElseThrow();
        switch (decided.getStatus()) {
            case CONFIRMED -> {
                log.info("Pedido {} já agendado, reenviando confirmação", orderId);
                publisher.confirmed(decided);
            }
            case REJECTED -> {
                log.info("Pedido {} já rejeitado, reenviando rejeição", orderId);
                publisher.rejected(decided);
            }
            case CANCELED -> log.info("Pedido {} cancelado antes do agendamento, vaga devolvida", orderId);
        }
    }

    private void releaseKeepingError(DeliverySlot slot, RuntimeException original) {
        log.warn("Falha ao gravar o agendamento, liberando a vaga {}", slot.getId());
        try {
            slots.release(slot.getId());
        } catch (RuntimeException releaseFailure) {
            original.addSuppressed(releaseFailure);
        }
    }
}
