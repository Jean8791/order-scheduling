package br.com.estudos.order.enums;

/**
 * Eventos que o pedido guarda no próprio documento até o {@code OutboxRelay} publicá-los no RabbitMQ.
 */
public enum OutboxEvent {
    ORDER_CREATED,
    ORDER_CANCELED
}
