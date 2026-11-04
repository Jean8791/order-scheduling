package br.com.estudos.order.enums;

/**
 * Ciclo de vida do pedido: aguarda agendamento, depois agendado, não agendado
 * ou cancelado.
 */
public enum OrderStatus {
    AWAITING_SCHEDULING,
    SCHEDULED,
    NOT_SCHEDULED,
    CANCELED;

    public boolean canBeCanceled() {
        return this != CANCELED;
    }
}
