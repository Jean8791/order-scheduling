package br.com.estudos.agendamento.mensageria;

import br.com.estudos.agendamento.servico.AgendamentoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventListener {

    private final AgendamentoService agendamentoService;

    public PedidoEventListener(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @RabbitListener(queues = Topologia.FILA_PEDIDO_CRIADO)
    public void pedidoCriado(Eventos.PedidoCriado evento) {
        agendamentoService.agendar(evento);
    }

    @RabbitListener(queues = Topologia.FILA_PEDIDO_CANCELADO)
    public void pedidoCancelado(Eventos.PedidoCancelado evento) {
        agendamentoService.cancelar(evento);
    }
}
