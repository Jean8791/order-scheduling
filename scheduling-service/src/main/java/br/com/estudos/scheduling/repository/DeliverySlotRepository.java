package br.com.estudos.scheduling.repository;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Acesso às janelas de entrega com {@code MongoTemplate}.
 *
 * <p>Quando a consulta foge do que o nome do método consegue expressar, usa-se
 * o {@code MongoTemplate} direto. A reserva de vaga usa {@code findAndModify}
 * com a condição "booked &lt; capacity": o MongoDB verifica e incrementa numa
 * única operação atômica, então dois pedidos nunca pegam a mesma última vaga.
 */
@Repository
public class DeliverySlotRepository {

    private static final Comparator<DeliverySlot> BY_DATE_AND_PERIOD =
            Comparator.comparing(DeliverySlot::getDate).thenComparing(DeliverySlot::getPeriod);

    private final MongoTemplate mongo;

    public DeliverySlotRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public DeliverySlot insert(DeliverySlot slot) {
        return mongo.insert(slot);
    }

    public boolean exists(String state, LocalDate date, Period period) {
        return mongo.exists(Query.query(Criteria.where("state").is(state).and("date").is(date).and("period").is(period)), DeliverySlot.class);
    }

    public List<DeliverySlot> list(String state, LocalDate from, LocalDate to) {
        Criteria criteria = Criteria.where("date").gte(from).lte(to);
        if (state != null) {
            criteria = criteria.and("state").is(state);
        }
        return sorted(mongo.find(Query.query(criteria), DeliverySlot.class));
    }

    public List<DeliverySlot> withAvailability(String state, LocalDate from, LocalDate to) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("state").is(state),
                Criteria.where("date").gte(from).lte(to),
                hasAvailability()));
        return sorted(mongo.find(query, DeliverySlot.class));
    }

    public Optional<DeliverySlot> book(String slotId) {
        Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(slotId), hasAvailability()));
        DeliverySlot updated = mongo.findAndModify(query, new Update().inc("booked", 1),
                FindAndModifyOptions.options().returnNew(true), DeliverySlot.class);
        return Optional.ofNullable(updated);
    }

    public boolean release(String slotId) {
        Query query = Query.query(Criteria.where("_id").is(slotId).and("booked").gt(0));
        return mongo.updateFirst(query, new Update().inc("booked", -1), DeliverySlot.class).getModifiedCount() > 0;
    }

    private static List<DeliverySlot> sorted(List<DeliverySlot> slots) {
        return slots.stream().sorted(BY_DATE_AND_PERIOD).toList();
    }

    private static Criteria hasAvailability() {
        return Criteria.expr(ComparisonOperators.valueOf("booked").lessThan("capacity"));
    }
}
