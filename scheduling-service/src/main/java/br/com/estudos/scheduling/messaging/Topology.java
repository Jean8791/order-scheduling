package br.com.estudos.scheduling.messaging;

public final class Topology {

    public static final String EXCHANGE_ORDERS = "orders.events";
    public static final String EXCHANGE_SCHEDULINGS = "schedulings.events";
    public static final String EXCHANGE_DLX = "scheduling-service.dlx";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_CANCELED = "order.canceled";
    public static final String RK_SCHEDULING_CONFIRMED = "scheduling.confirmed";
    public static final String RK_SCHEDULING_REJECTED = "scheduling.rejected";

    public static final String QUEUE_ORDER_CREATED = "scheduling-service.order-created";
    public static final String QUEUE_ORDER_CANCELED = "scheduling-service.order-canceled";

    private Topology() {
    }
}
