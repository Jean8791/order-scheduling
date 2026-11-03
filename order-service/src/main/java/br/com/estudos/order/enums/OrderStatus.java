package br.com.estudos.order.enums;

public enum OrderStatus {
    AWAITING_SCHEDULING,
    SCHEDULED,
    NOT_SCHEDULED,
    CANCELED;

    public boolean canBeCanceled() {
        return this != CANCELED;
    }
}
