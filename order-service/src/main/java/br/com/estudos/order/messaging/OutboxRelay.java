package br.com.estudos.order.messaging;

import br.com.estudos.order.entity.Order;
import br.com.estudos.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica no RabbitMQ os eventos que ficaram pendentes nos pedidos e só depois os limpa.
 *
 * <p>Se o serviço cair ou o RabbitMQ estiver fora do ar, os eventos continuam gravados e saem na próxima rodada:
 * a entrega é "pelo menos uma vez" (pode repetir, nunca se perde). Com várias instâncias do order-service, mais de
 * uma pode publicar o mesmo evento; os consumidores já são idempotentes.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final OrderEventPublisher publisher;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox, OrderEventPublisher publisher,
                       @Value("${outbox.batch-size:500}") int batchSize) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval:200ms}")
    public void relay() {
        List<Order> batch;
        do {
            batch = outbox.findPending(batchSize);
            if (!batch.isEmpty()) {
                publisher.publish(batch);
                outbox.clear(batch);
                log.debug("{} pedidos com eventos publicados", batch.size());
            }
        } while (batch.size() == batchSize);
    }
}
