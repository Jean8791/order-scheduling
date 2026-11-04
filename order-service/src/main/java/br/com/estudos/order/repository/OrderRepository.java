package br.com.estudos.order.repository;

import br.com.estudos.order.entity.Order;
import br.com.estudos.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Acesso à coleção de pedidos (Spring Data MongoDB).
 *
 * <p>Os métodos são <i>derived queries</i>: o Spring lê o nome e monta a
 * consulta sozinho. {@code findByIdAndOwner} vira {@code { _id: ?, owner: ?
 * }}, e com {@code Pageable} a consulta já sai paginada e ordenada.
 */
public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByIdAndOwner(String id, String owner);

    Page<Order> findByOwner(String owner, Pageable pageable);

    Page<Order> findByOwnerAndStatus(String owner, OrderStatus status, Pageable pageable);
}
