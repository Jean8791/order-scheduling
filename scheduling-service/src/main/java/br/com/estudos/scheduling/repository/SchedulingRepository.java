package br.com.estudos.scheduling.repository;

import br.com.estudos.scheduling.entity.Scheduling;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SchedulingRepository extends MongoRepository<Scheduling, String> {
}
