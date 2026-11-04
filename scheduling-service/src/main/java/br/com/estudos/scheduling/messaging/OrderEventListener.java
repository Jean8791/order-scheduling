package br.com.estudos.scheduling.messaging;

import br.com.estudos.scheduling.service.SchedulingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome os eventos de pedido.
 *
 * <p>{@code @RabbitListener} faz o Spring escutar a fila e chamar o método a
 * cada mensagem, já convertida do JSON para o record. Se o método lançar
 * exceção, o Spring tenta de novo (configurado em application.properties) e,
 * esgotadas as tentativas, a mensagem vai para a fila {@code .dlq}.
 */
@Component
public class OrderEventListener {

    private final SchedulingService schedulingService;

    public OrderEventListener(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @RabbitListener(queues = Topology.QUEUE_ORDER_CREATED)
    public void orderCreated(Events.OrderCreated event) {
        schedulingService.schedule(event);
    }

    @RabbitListener(queues = Topology.QUEUE_ORDER_CANCELED)
    public void orderCanceled(Events.OrderCanceled event) {
        schedulingService.cancel(event);
    }
}
