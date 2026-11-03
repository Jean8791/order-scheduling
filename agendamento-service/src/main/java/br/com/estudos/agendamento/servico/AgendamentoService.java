package br.com.estudos.agendamento.servico;

import br.com.estudos.agendamento.config.AgendamentoProperties;
import br.com.estudos.agendamento.dominio.Agendamento;
import br.com.estudos.agendamento.dominio.AgendamentoRepository;
import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.dominio.JanelaRepository;
import br.com.estudos.agendamento.dominio.StatusAgendamento;
import br.com.estudos.agendamento.mensageria.AgendamentoEventPublisher;
import br.com.estudos.agendamento.mensageria.Eventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class AgendamentoService {

    static final String SEM_JANELA = "Sem janela de entrega disponível até %s para a UF %s";

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    private final AgendamentoRepository agendamentos;
    private final JanelaRepository janelas;
    private final AgendamentoEventPublisher publisher;
    private final AgendamentoProperties properties;
    private final Clock clock;

    public AgendamentoService(AgendamentoRepository agendamentos, JanelaRepository janelas,
                              AgendamentoEventPublisher publisher, AgendamentoProperties properties, Clock clock) {
        this.agendamentos = agendamentos;
        this.janelas = janelas;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<Agendamento> buscar(String pedidoId) {
        return agendamentos.findById(pedidoId);
    }

    public void agendar(Eventos.PedidoCriado evento) {
        Agendamento agendamento = iniciar(evento.pedidoId());

        switch (agendamento.getStatus()) {
            case CONFIRMADO -> {
                log.info("Pedido {} já agendado, reenviando confirmação", evento.pedidoId());
                publisher.confirmado(agendamento);
                return;
            }
            case RECUSADO -> {
                log.info("Pedido {} já recusado, reenviando recusa", evento.pedidoId());
                publisher.recusado(agendamento);
                return;
            }
            case CANCELADO -> {
                log.info("Pedido {} cancelado antes do agendamento, nada a fazer", evento.pedidoId());
                return;
            }
            case PROCESSANDO -> {
            }
        }

        LocalDate hoje = LocalDate.now(clock.withZone(properties.zona()));
        LocalDate inicio = evento.dataEntregaDesejada().isBefore(hoje) ? hoje : evento.dataEntregaDesejada();
        LocalDate fim = evento.dataEntregaDesejada().plusDays(properties.diasBusca());

        Optional<JanelaEntrega> reservada = fim.isBefore(inicio)
                ? Optional.empty()
                : janelas.comVagas(evento.uf(), inicio, fim).stream()
                        .map(candidata -> janelas.reservarVaga(candidata.getId()))
                        .flatMap(Optional::stream)
                        .findFirst();

        if (reservada.isPresent()) {
            agendamento.confirmar(reservada.get(), clock.instant());
            Agendamento salvo = agendamentos.save(agendamento);
            publisher.confirmado(salvo);
            log.info("Pedido {} agendado na janela {} ({} {})", salvo.getPedidoId(), salvo.getJanelaId(), salvo.getData(), salvo.getPeriodo());
        } else {
            agendamento.recusar(String.format(SEM_JANELA, fim, evento.uf()), clock.instant());
            Agendamento salvo = agendamentos.save(agendamento);
            publisher.recusado(salvo);
            log.info("Pedido {} sem janela disponível", salvo.getPedidoId());
        }
    }

    public void cancelar(Eventos.PedidoCancelado evento) {
        Optional<Agendamento> existente = agendamentos.findById(evento.pedidoId());

        if (existente.isEmpty()) {
            try {
                agendamentos.insert(Agendamento.canceladoAntesDeProcessar(evento.pedidoId(), evento.motivo(), clock.instant()));
                log.info("Pedido {} cancelado antes do agendamento", evento.pedidoId());
                return;
            } catch (DuplicateKeyException corrida) {
                existente = agendamentos.findById(evento.pedidoId());
            }
        }

        Agendamento agendamento = existente.orElseThrow();
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            return;
        }
        if (agendamento.ocupaJanela()) {
            janelas.liberarVaga(agendamento.getJanelaId());
        }
        agendamento.cancelar(evento.motivo(), clock.instant());
        agendamentos.save(agendamento);
        log.info("Agendamento do pedido {} cancelado", evento.pedidoId());
    }

    private Agendamento iniciar(String pedidoId) {
        Optional<Agendamento> existente = agendamentos.findById(pedidoId);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            return agendamentos.insert(Agendamento.processando(pedidoId, clock.instant()));
        } catch (DuplicateKeyException corrida) {
            return agendamentos.findById(pedidoId).orElseThrow();
        }
    }
}
