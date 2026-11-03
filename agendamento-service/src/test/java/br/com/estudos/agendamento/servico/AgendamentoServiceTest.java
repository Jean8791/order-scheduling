package br.com.estudos.agendamento.servico;

import br.com.estudos.agendamento.config.AgendamentoProperties;
import br.com.estudos.agendamento.dominio.Agendamento;
import br.com.estudos.agendamento.dominio.AgendamentoRepository;
import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.dominio.JanelaRepository;
import br.com.estudos.agendamento.dominio.Periodo;
import br.com.estudos.agendamento.dominio.StatusAgendamento;
import br.com.estudos.agendamento.mensageria.AgendamentoEventPublisher;
import br.com.estudos.agendamento.mensageria.Eventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final LocalDate DESEJADA = LocalDate.of(2026, 11, 10);

    @Mock
    AgendamentoRepository agendamentos;

    @Mock
    JanelaRepository janelas;

    @Mock
    AgendamentoEventPublisher publisher;

    AgendamentoService service;

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(agendamentos, janelas, publisher,
                new AgendamentoProperties(7, ZoneId.of("America/Sao_Paulo")), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Eventos.PedidoCriado pedidoCriado(String pedidoId, LocalDate desejada) {
        return new Eventos.PedidoCriado("e1", AGORA, pedidoId, "01310100", "SP", desejada, 10);
    }

    private static JanelaEntrega janela(LocalDate data, Periodo periodo) {
        return JanelaEntrega.nova("SP", data, periodo, 1, AGORA);
    }

    private void novoAgendamento(String pedidoId) {
        when(agendamentos.findById(pedidoId)).thenReturn(Optional.empty());
        when(agendamentos.insert(any(Agendamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(agendamentos.save(any(Agendamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    void reservaAPrimeiraJanelaQueAindaTemVaga() {
        novoAgendamento("p1");
        JanelaEntrega lotadaNaCorrida = janela(DESEJADA, Periodo.MANHA);
        JanelaEntrega livre = janela(DESEJADA, Periodo.TARDE);
        when(janelas.comVagas("SP", DESEJADA, DESEJADA.plusDays(7))).thenReturn(List.of(lotadaNaCorrida, livre));
        when(janelas.reservarVaga(lotadaNaCorrida.getId())).thenReturn(Optional.empty());
        when(janelas.reservarVaga(livre.getId())).thenReturn(Optional.of(livre));

        service.agendar(pedidoCriado("p1", DESEJADA));

        ArgumentCaptor<Agendamento> confirmado = ArgumentCaptor.forClass(Agendamento.class);
        verify(publisher).confirmado(confirmado.capture());
        assertThat(confirmado.getValue().getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(confirmado.getValue().getJanelaId()).isEqualTo(livre.getId());
        assertThat(confirmado.getValue().getPeriodo()).isEqualTo(Periodo.TARDE);
        verify(publisher, never()).recusado(any());
    }

    @Test
    void semVagaNoPrazoRecusaComMotivo() {
        novoAgendamento("p1");
        when(janelas.comVagas("SP", DESEJADA, DESEJADA.plusDays(7))).thenReturn(List.of());

        service.agendar(pedidoCriado("p1", DESEJADA));

        ArgumentCaptor<Agendamento> recusado = ArgumentCaptor.forClass(Agendamento.class);
        verify(publisher).recusado(recusado.capture());
        assertThat(recusado.getValue().getStatus()).isEqualTo(StatusAgendamento.RECUSADO);
        assertThat(recusado.getValue().getMotivo()).contains("2026-11-17").contains("SP");
    }

    @Test
    void dataDesejadaNoPassadoBuscaAPartirDeHoje() {
        novoAgendamento("p1");
        LocalDate hojeEmSaoPaulo = LocalDate.of(2026, 11, 3);
        LocalDate desejadaPassada = LocalDate.of(2026, 11, 1);
        when(janelas.comVagas("SP", hojeEmSaoPaulo, desejadaPassada.plusDays(7))).thenReturn(List.of());

        service.agendar(pedidoCriado("p1", desejadaPassada));

        verify(janelas).comVagas("SP", hojeEmSaoPaulo, LocalDate.of(2026, 11, 8));
    }

    @Test
    void dataDesejadaMuitoAntigaRecusaSemConsultarJanelas() {
        novoAgendamento("p1");

        service.agendar(pedidoCriado("p1", LocalDate.of(2026, 10, 1)));

        verify(janelas, never()).comVagas(anyString(), any(), any());
        verify(publisher).recusado(any());
    }

    @Test
    void eventoDuplicadoDePedidoJaAgendadoSoReenviaAConfirmacao() {
        Agendamento existente = Agendamento.processando("p1", AGORA);
        existente.confirmar(janela(DESEJADA, Periodo.MANHA), AGORA);
        when(agendamentos.findById("p1")).thenReturn(Optional.of(existente));

        service.agendar(pedidoCriado("p1", DESEJADA));

        verify(publisher).confirmado(existente);
        verifyNoInteractions(janelas);
        verify(agendamentos, never()).save(any());
    }

    @Test
    void pedidoCanceladoAntesDoAgendamentoNaoReservaVaga() {
        when(agendamentos.findById("p1")).thenReturn(Optional.of(Agendamento.canceladoAntesDeProcessar("p1", "desistiu", AGORA)));

        service.agendar(pedidoCriado("p1", DESEJADA));

        verifyNoInteractions(janelas, publisher);
    }

    @Test
    void consumoConcorrenteDoMesmoEventoUsaORegistroQueGanhouACorrida() {
        Agendamento vencedor = Agendamento.processando("p1", AGORA);
        vencedor.recusar("Sem janela", AGORA);
        when(agendamentos.findById("p1")).thenReturn(Optional.empty(), Optional.of(vencedor));
        when(agendamentos.insert(any(Agendamento.class))).thenThrow(new DuplicateKeyException("_id duplicado"));

        service.agendar(pedidoCriado("p1", DESEJADA));

        verify(publisher).recusado(vencedor);
        verifyNoInteractions(janelas);
    }

    @Test
    void cancelarAgendamentoConfirmadoLiberaAVaga() {
        JanelaEntrega janela = janela(DESEJADA, Periodo.MANHA);
        Agendamento confirmado = Agendamento.processando("p1", AGORA);
        confirmado.confirmar(janela, AGORA);
        when(agendamentos.findById("p1")).thenReturn(Optional.of(confirmado));

        service.cancelar(new Eventos.PedidoCancelado("e2", AGORA, "p1", "Cliente desistiu"));

        verify(janelas).liberarVaga(janela.getId());
        verify(agendamentos).save(confirmado);
        assertThat(confirmado.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void cancelarRecusadoNaoMexeEmJanela() {
        Agendamento recusado = Agendamento.processando("p1", AGORA);
        recusado.recusar("Sem janela", AGORA);
        when(agendamentos.findById("p1")).thenReturn(Optional.of(recusado));

        service.cancelar(new Eventos.PedidoCancelado("e2", AGORA, "p1", "Cliente desistiu"));

        verifyNoInteractions(janelas);
        assertThat(recusado.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void cancelamentoQueChegaAntesDoPedidoCriadoGravaMarcador() {
        when(agendamentos.findById("p1")).thenReturn(Optional.empty());

        service.cancelar(new Eventos.PedidoCancelado("e2", AGORA, "p1", "Cliente desistiu"));

        ArgumentCaptor<Agendamento> marcador = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentos).insert(marcador.capture());
        assertThat(marcador.getValue().getPedidoId()).isEqualTo("p1");
        assertThat(marcador.getValue().getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        verifyNoInteractions(janelas);
    }

    @Test
    void cancelamentoRepetidoEhIgnorado() {
        when(agendamentos.findById("p1")).thenReturn(Optional.of(Agendamento.canceladoAntesDeProcessar("p1", "desistiu", AGORA)));

        service.cancelar(new Eventos.PedidoCancelado("e3", AGORA, "p1", "de novo"));

        verify(agendamentos, never()).save(any());
        verifyNoInteractions(janelas);
    }
}
