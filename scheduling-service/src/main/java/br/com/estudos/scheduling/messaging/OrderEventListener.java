package br.com.estudos.scheduling.messaging;

import br.com.estudos.scheduling.service.SchedulingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
