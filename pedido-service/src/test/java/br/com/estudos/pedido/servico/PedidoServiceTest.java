package br.com.estudos.pedido.servico;

import br.com.estudos.pedido.api.dto.CriarPedidoRequest;
import br.com.estudos.pedido.dominio.Endereco;
import br.com.estudos.pedido.dominio.ItemPedido;
import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.PedidoNaoEncontradoException;
import br.com.estudos.pedido.dominio.PedidoRepository;
import br.com.estudos.pedido.dominio.StatusPedido;
import br.com.estudos.pedido.mensageria.Eventos;
import br.com.estudos.pedido.mensageria.PedidoEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");

    @Mock
    PedidoRepository repository;

    @Mock
    PedidoEventPublisher publisher;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(repository, publisher, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static CriarPedidoRequest request() {
        return new CriarPedidoRequest("Loja Exemplo",
                new CriarPedidoRequest.EnderecoRequest("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new CriarPedidoRequest.ItemRequest("CAFE-500G", "Café 500g", 10)),
                LocalDate.of(2026, 12, 10));
    }

    private static Pedido pedidoAguardando() {
        return Pedido.novo("Loja Exemplo", new Endereco("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new ItemPedido("CAFE-500G", "Café 500g", 10)), LocalDate.of(2026, 12, 10), AGORA);
    }

    @Test
    void criarSalvaEPublicaPedidoCriado() {
        when(repository.save(any(Pedido.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Pedido pedido = service.criar(request());

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_AGENDAMENTO);
        assertThat(pedido.getItens()).containsExactly(new ItemPedido("CAFE-500G", "Café 500g", 10));
        verify(publisher).pedidoCriado(pedido);
    }

    @Test
    void buscarInexistenteLancaNaoEncontrado() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar("x")).isInstanceOf(PedidoNaoEncontradoException.class);
    }

    @Test
    void confirmacaoDeAgendamentoAtualizaOPedido() {
        Pedido pedido = pedidoAguardando();
        when(repository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        service.registrarAgendamentoConfirmado(new Eventos.AgendamentoConfirmado("e1", AGORA, pedido.getId(),
                "janela-1", LocalDate.of(2026, 12, 10), "MANHA"));

        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getStatus()).isEqualTo(StatusPedido.AGENDADO);
        assertThat(salvo.getValue().getAgendamento().janelaId()).isEqualTo("janela-1");
    }

    @Test
    void recusaDeAgendamentoGuardaMotivo() {
        Pedido pedido = pedidoAguardando();
        when(repository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        service.registrarAgendamentoRecusado(new Eventos.AgendamentoRecusado("e1", AGORA, pedido.getId(), "Sem janela"));

        verify(repository).save(pedido);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.SEM_AGENDA);
        assertThat(pedido.getMotivo()).isEqualTo("Sem janela");
    }

    @Test
    void eventoParaPedidoJaCanceladoNaoGravaNada() {
        Pedido pedido = pedidoAguardando();
        pedido.cancelar("Cliente desistiu", AGORA);
        when(repository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        service.registrarAgendamentoConfirmado(new Eventos.AgendamentoConfirmado("e1", AGORA, pedido.getId(),
                "janela-1", LocalDate.of(2026, 12, 10), "MANHA"));

        verify(repository, never()).save(any());
    }

    @Test
    void cancelarSalvaEPublicaPedidoCancelado() {
        Pedido pedido = pedidoAguardando();
        when(repository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(repository.save(pedido)).thenReturn(pedido);

        Pedido cancelado = service.cancelar(pedido.getId(), "Cliente desistiu");

        assertThat(cancelado.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        verify(publisher).pedidoCancelado(pedido);
    }
}
