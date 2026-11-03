package br.com.estudos.order.messaging;

public final class Topology {

    public static final String EXCHANGE_ORDERS = "orders.events";
    public static final String EXCHANGE_SCHEDULINGS = "schedulings.events";
    public static final String EXCHANGE_DLX = "order-service.dlx";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_CANCELED = "order.canceled";
    public static final String RK_SCHEDULING_CONFIRMED = "scheduling.confirmed";
    public static final String RK_SCHEDULING_REJECTED = "scheduling.rejected";

    public static final String QUEUE_SCHEDULING_CONFIRMED = "order-service.scheduling-confirmed";
    public static final String QUEUE_SCHEDULING_REJECTED = "order-service.scheduling-rejected";

    private Topology() {
    }
}
