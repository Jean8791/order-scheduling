package br.com.estudos.pedido.mensageria;

import br.com.estudos.pedido.servico.PedidoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AgendamentoResultadoListener {

    private final PedidoService pedidoService;

    public AgendamentoResultadoListener(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @RabbitListener(queues = Topologia.FILA_AGENDAMENTO_CONFIRMADO)
    public void confirmado(Eventos.AgendamentoConfirmado evento) {
        pedidoService.registrarAgendamentoConfirmado(evento);
    }

    @RabbitListener(queues = Topologia.FILA_AGENDAMENTO_RECUSADO)
    public void recusado(Eventos.AgendamentoRecusado evento) {
        pedidoService.registrarAgendamentoRecusado(evento);
    }
}
