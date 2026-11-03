package br.com.estudos.agendamento.mensageria;

import br.com.estudos.agendamento.dominio.Agendamento;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class AgendamentoEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public AgendamentoEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void confirmado(Agendamento agendamento) {
        Eventos.AgendamentoConfirmado evento = new Eventos.AgendamentoConfirmado(UUID.randomUUID().toString(), clock.instant(),
                agendamento.getPedidoId(), agendamento.getJanelaId(), agendamento.getData(), agendamento.getPeriodo().name());
        rabbitTemplate.convertAndSend(Topologia.EXCHANGE_AGENDAMENTOS, Topologia.RK_AGENDAMENTO_CONFIRMADO, evento);
    }

    public void recusado(Agendamento agendamento) {
        Eventos.AgendamentoRecusado evento = new Eventos.AgendamentoRecusado(UUID.randomUUID().toString(), clock.instant(),
                agendamento.getPedidoId(), agendamento.getMotivo());
        rabbitTemplate.convertAndSend(Topologia.EXCHANGE_AGENDAMENTOS, Topologia.RK_AGENDAMENTO_RECUSADO, evento);
    }
}
