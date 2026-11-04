package br.com.estudos.scheduling.messaging;

import br.com.estudos.scheduling.entity.Scheduling;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Publica o resultado do agendamento no RabbitMQ com o {@code RabbitTemplate}.
 */
@Component
public class SchedulingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public SchedulingEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void confirmed(Scheduling scheduling) {
        Events.SchedulingConfirmed event = new Events.SchedulingConfirmed(UUID.randomUUID().toString(), clock.instant(),
                scheduling.getOrderId(), scheduling.getSlotId(), scheduling.getDate(), scheduling.getPeriod().name());
        rabbitTemplate.convertAndSend(Topology.EXCHANGE_SCHEDULINGS, Topology.RK_SCHEDULING_CONFIRMED, event);
    }

    public void rejected(Scheduling scheduling) {
        Events.SchedulingRejected event = new Events.SchedulingRejected(UUID.randomUUID().toString(), clock.instant(),
                scheduling.getOrderId(), scheduling.getReason());
        rabbitTemplate.convertAndSend(Topology.EXCHANGE_SCHEDULINGS, Topology.RK_SCHEDULING_REJECTED, event);
    }
}
