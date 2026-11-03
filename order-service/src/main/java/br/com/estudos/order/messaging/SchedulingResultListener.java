package br.com.estudos.order.messaging;

import br.com.estudos.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SchedulingResultListener {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = Topology.QUEUE_SCHEDULING_CONFIRMED)
    public void confirmed(Events.SchedulingConfirmed event) {
        orderService.registerSchedulingConfirmed(event);
    }

    @RabbitListener(queues = Topology.QUEUE_SCHEDULING_REJECTED)
    public void rejected(Events.SchedulingRejected event) {
        orderService.registerSchedulingRejected(event);
    }
}
