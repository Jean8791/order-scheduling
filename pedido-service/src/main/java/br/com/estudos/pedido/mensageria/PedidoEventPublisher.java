package br.com.estudos.pedido.mensageria;

import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.ItemPedido;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class PedidoEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void pedidoCriado(Pedido pedido) {
        int quantidade = pedido.getItens().stream().mapToInt(ItemPedido::quantidade).sum();
        Eventos.PedidoCriado evento = new Eventos.PedidoCriado(UUID.randomUUID().toString(), clock.instant(),
                pedido.getId(), pedido.getEndereco().cep(), pedido.getEndereco().uf(),
                pedido.getDataEntregaDesejada(), quantidade);
        rabbitTemplate.convertAndSend(Topologia.EXCHANGE_PEDIDOS, Topologia.RK_PEDIDO_CRIADO, evento);
    }

    public void pedidoCancelado(Pedido pedido) {
        Eventos.PedidoCancelado evento = new Eventos.PedidoCancelado(UUID.randomUUID().toString(), clock.instant(),
                pedido.getId(), pedido.getMotivo());
        rabbitTemplate.convertAndSend(Topologia.EXCHANGE_PEDIDOS, Topologia.RK_PEDIDO_CANCELADO, evento);
    }
}
