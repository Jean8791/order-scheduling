package br.com.estudos.scheduling.messaging;

/**
 * Nomes de exchanges, routing keys e filas do RabbitMQ.
 *
 * <p>Exchange do tipo topic: quem publica manda para a exchange com uma
 * routing key ({@code order.created}); cada fila ligada com aquela chave
 * recebe uma cópia. A DLX ({@code .dlx}) recebe as mensagens que falharam
 * depois das tentativas, para análise.
 */
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
