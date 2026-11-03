package br.com.estudos.agendamento.dominio;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JanelaRepository {

    private final MongoTemplate mongo;

    public JanelaRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public JanelaEntrega inserir(JanelaEntrega janela) {
        return mongo.insert(janela);
    }

    public boolean existe(String uf, LocalDate data, Periodo periodo) {
        return mongo.exists(Query.query(Criteria.where("uf").is(uf).and("data").is(data).and("periodo").is(periodo)), JanelaEntrega.class);
    }

    public List<JanelaEntrega> listar(String uf, LocalDate de, LocalDate ate) {
        Criteria criteria = Criteria.where("data").gte(de).lte(ate);
        if (uf != null) {
            criteria = criteria.and("uf").is(uf);
        }
        return mongo.find(Query.query(criteria).with(Sort.by("data", "periodo")), JanelaEntrega.class);
    }

    public List<JanelaEntrega> comVagas(String uf, LocalDate de, LocalDate ate) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("uf").is(uf),
                Criteria.where("data").gte(de).lte(ate),
                temVaga()));
        return mongo.find(query.with(Sort.by("data", "periodo")), JanelaEntrega.class);
    }

    public Optional<JanelaEntrega> reservarVaga(String janelaId) {
        Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(janelaId), temVaga()));
        JanelaEntrega atualizada = mongo.findAndModify(query, new Update().inc("ocupados", 1),
                FindAndModifyOptions.options().returnNew(true), JanelaEntrega.class);
        return Optional.ofNullable(atualizada);
    }

    public boolean liberarVaga(String janelaId) {
        Query query = Query.query(Criteria.where("_id").is(janelaId).and("ocupados").gt(0));
        return mongo.updateFirst(query, new Update().inc("ocupados", -1), JanelaEntrega.class).getModifiedCount() > 0;
    }

    private static Criteria temVaga() {
        return Criteria.expr(ComparisonOperators.valueOf("ocupados").lessThan("capacidade"));
    }
}
