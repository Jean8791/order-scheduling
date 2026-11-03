package br.com.estudos.auth.repository;

import br.com.estudos.auth.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
}
