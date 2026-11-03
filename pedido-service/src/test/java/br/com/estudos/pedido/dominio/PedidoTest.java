package br.com.estudos.pedido.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant DEPOIS = AGORA.plusSeconds(60);

    private Pedido novoPedido() {
        return Pedido.novo("Loja Exemplo",
                new Endereco("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new ItemPedido("CAFE-500G", "Café 500g", 10)),
                LocalDate.of(2026, 12, 10), AGORA);
    }

    @Test
    void novoPedidoNasceAguardandoAgendamento() {
        Pedido pedido = novoPedido();

        assertThat(pedido.getId()).isNotBlank();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_AGENDAMENTO);
        assertThat(pedido.getCriadoEm()).isEqualTo(AGORA);
        assertThat(pedido.getAgendamento()).isNull();
    }

    @Test
    void confirmaAgendamentoQuandoAguardando() {
        Pedido pedido = novoPedido();
        AgendamentoPedido agendamento = new AgendamentoPedido("janela-1", LocalDate.of(2026, 12, 10), "MANHA");

        assertThat(pedido.confirmarAgendamento(agendamento, DEPOIS)).isTrue();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGENDADO);
        assertThat(pedido.getAgendamento()).isEqualTo(agendamento);
        assertThat(pedido.getAtualizadoEm()).isEqualTo(DEPOIS);
    }

    @Test
    void recusaAgendamentoGuardaMotivo() {
        Pedido pedido = novoPedido();

        assertThat(pedido.recusarAgendamento("Sem janela", DEPOIS)).isTrue();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.SEM_AGENDA);
        assertThat(pedido.getMotivo()).isEqualTo("Sem janela");
    }

    @Test
    void eventoRepetidoDeAgendamentoEhIgnorado() {
        Pedido pedido = novoPedido();
        AgendamentoPedido primeiro = new AgendamentoPedido("janela-1", LocalDate.of(2026, 12, 10), "MANHA");
        pedido.confirmarAgendamento(primeiro, DEPOIS);

        assertThat(pedido.confirmarAgendamento(new AgendamentoPedido("janela-2", LocalDate.of(2026, 12, 11), "TARDE"), DEPOIS)).isFalse();
        assertThat(pedido.recusarAgendamento("tarde demais", DEPOIS)).isFalse();
        assertThat(pedido.getAgendamento()).isEqualTo(primeiro);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGENDADO);
    }

    @Test
    void confirmacaoQueChegaDepoisDoCancelamentoNaoReabreOPedido() {
        Pedido pedido = novoPedido();
        pedido.cancelar("Cliente desistiu", DEPOIS);

        assertThat(pedido.confirmarAgendamento(new AgendamentoPedido("janela-1", LocalDate.of(2026, 12, 10), "MANHA"), DEPOIS)).isFalse();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void naoCancelaDuasVezes() {
        Pedido pedido = novoPedido();
        pedido.cancelar("Cliente desistiu", DEPOIS);

        assertThatThrownBy(() -> pedido.cancelar("de novo", DEPOIS))
                .isInstanceOf(OperacaoInvalidaException.class)
                .hasMessageContaining("já está cancelado");
    }
}
