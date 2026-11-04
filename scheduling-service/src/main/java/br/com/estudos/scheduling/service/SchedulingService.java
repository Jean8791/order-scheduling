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
        Scheduling scheduling = start(event.orderId());

        switch (scheduling.getStatus()) {
            case CONFIRMED -> {
                log.info("Pedido {} já agendado, reenviando confirmação", event.orderId());
                publisher.confirmed(scheduling);
                return;
            }
            case REJECTED -> {
                log.info("Pedido {} já rejeitado, reenviando rejeição", event.orderId());
                publisher.rejected(scheduling);
                return;
            }
            case CANCELED -> {
                log.info("Pedido {} cancelado antes do agendamento, nada a fazer", event.orderId());
                return;
            }
            case PROCESSING -> {
            }
        }

        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        LocalDate from = event.desiredDeliveryDate().isBefore(today) ? today : event.desiredDeliveryDate();
        LocalDate to = event.desiredDeliveryDate().plusDays(properties.searchDays());

        Optional<DeliverySlot> booked = to.isBefore(from)
                ? Optional.empty()
                : slots.withAvailability(event.state(), from, to).stream()
                        .map(candidate -> slots.book(candidate.getId()))
                        .flatMap(Optional::stream)
                        .findFirst();

        if (booked.isPresent()) {
            scheduling.confirm(booked.get(), clock.instant());
            Scheduling saved = saveOrReleaseSlot(scheduling);
            publisher.confirmed(saved);
            log.info("Pedido {} agendado na janela {} ({} {})", saved.getOrderId(), saved.getSlotId(), saved.getDate(), saved.getPeriod());
        } else {
            scheduling.reject(String.format(NO_SLOT_REASON, to, event.state()), clock.instant());
            Scheduling saved = schedulings.save(scheduling);
            publisher.rejected(saved);
            log.info("Pedido {} sem janela disponível", saved.getOrderId());
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

    private Scheduling saveOrReleaseSlot(Scheduling scheduling) {
        try {
            return schedulings.save(scheduling);
        } catch (RuntimeException e) {
            log.warn("Falha ao gravar o agendamento do pedido {}, liberando a vaga {}", scheduling.getOrderId(), scheduling.getSlotId());
            try {
                slots.release(scheduling.getSlotId());
            } catch (RuntimeException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }
            throw e;
        }
    }

    private Scheduling start(String orderId) {
        Optional<Scheduling> existing = schedulings.findById(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return schedulings.insert(Scheduling.processing(orderId, clock.instant()));
        } catch (DuplicateKeyException race) {
            return schedulings.findById(orderId).orElseThrow();
        }
    }
}
