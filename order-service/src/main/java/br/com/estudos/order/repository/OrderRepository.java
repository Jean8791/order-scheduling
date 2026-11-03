package br.com.estudos.order.repository;

import br.com.estudos.order.entity.Order;
import br.com.estudos.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
