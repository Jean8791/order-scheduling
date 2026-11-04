package br.com.estudos.order.messaging;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Eventos trocados entre os serviços pelo RabbitMQ, serializados em JSON.
 */
public final class Events {

    private Events() {
    }

    public record OrderCreated(String eventId, Instant occurredAt, String orderId, String zipCode, String state,
                               LocalDate desiredDeliveryDate, int itemCount) {
    }

    public record OrderCanceled(String eventId, Instant occurredAt, String orderId, String reason) {
    }

    public record SchedulingConfirmed(String eventId, Instant occurredAt, String orderId, String slotId,
                                      LocalDate date, String period) {
    }

    public record SchedulingRejected(String eventId, Instant occurredAt, String orderId, String reason) {
    }
}
