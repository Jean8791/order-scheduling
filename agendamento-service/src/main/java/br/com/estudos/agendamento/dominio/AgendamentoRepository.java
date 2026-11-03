package br.com.estudos.agendamento.dominio;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AgendamentoRepository extends MongoRepository<Agendamento, String> {
}
