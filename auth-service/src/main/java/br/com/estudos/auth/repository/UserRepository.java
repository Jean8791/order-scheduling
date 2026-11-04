package br.com.estudos.auth.repository;

import br.com.estudos.auth.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Acesso à coleção de usuários (Spring Data MongoDB).
 *
 * <p>Basta estender {@code MongoRepository}: o Spring gera a implementação em
 * tempo de execução, com {@code save}, {@code findById}, {@code insert} etc. O
 * id do usuário é o e-mail.
 */
public interface UserRepository extends MongoRepository<User, String> {
}
