package br.com.estudos.pedido.dominio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    Page<Pedido> findByStatus(StatusPedido status, Pageable pageable);
}
