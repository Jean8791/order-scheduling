package br.com.estudos.order.repository;

import br.com.estudos.order.entity.Order;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Acesso aos eventos pendentes guardados nos pedidos (padrão <i>outbox</i>).
 *
 * <p>O MongoDB do projeto não exige replica set, então não há transação entre coleções. O evento pendente fica
 * <b>dentro do documento do pedido</b>, e uma gravação só já é atômica: ou o pedido e o evento existem, ou nenhum
 * dos dois. A consulta de pendentes usa o campo {@code pendingEvents}; com muitos pedidos ela pede o índice esparso
 * {@code {pendingEvents: 1}} (ver o README), que só contém os pedidos com evento por publicar.
 */
@Repository
public class OutboxRepository {

    private final MongoTemplate mongo;

    public OutboxRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Order> findPending(int limit) {
        return mongo.find(Query.query(Criteria.where("pendingEvents").exists(true)).limit(limit), Order.class);
    }

    /**
     * Limpa os eventos já publicados. Só limpa o pedido cujo {@code pendingEvents} continua igual ao que o relay
     * leu: se um evento novo entrou no meio (um cancelamento, por exemplo), o pedido segue pendente e o evento
     * velho é publicado de novo, o que os consumidores toleram (são idempotentes).
     */
    public void clear(List<Order> published) {
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Order.class);
        for (Order order : published) {
            bulk.updateOne(Query.query(Criteria.where("_id").is(order.getId()).and("pendingEvents").is(order.getPendingEvents())),
                    new Update().unset("pendingEvents"));
        }
        bulk.execute();
    }
}
