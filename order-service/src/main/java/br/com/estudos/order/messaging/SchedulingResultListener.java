package br.com.estudos.order.messaging;

import br.com.estudos.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome o resultado do agendamento e atualiza o pedido.
 *
 * <p>{@code @RabbitListener} escuta a fila e converte o JSON da mensagem no
 * record do evento. Falhas são reprocessadas e, depois, vão para a {@code
 * .dlq}.
 */
@Component
public class SchedulingResultListener {

    private final OrderService orderService;

    public SchedulingResultListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = Topology.QUEUE_SCHEDULING_CONFIRMED)
    public void confirmed(Events.SchedulingConfirmed event) {
        orderService.registerSchedulingConfirmed(event);
    }

    @RabbitListener(queues = Topology.QUEUE_SCHEDULING_REJECTED)
    public void rejected(Events.SchedulingRejected event) {
        orderService.registerSchedulingRejected(event);
    }
}
