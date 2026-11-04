package br.com.estudos.scheduling.repository;

import br.com.estudos.scheduling.entity.Scheduling;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Acesso à coleção de agendamentos (Spring Data MongoDB). O id é o id do
 * pedido.
 */
public interface SchedulingRepository extends MongoRepository<Scheduling, String> {
}
