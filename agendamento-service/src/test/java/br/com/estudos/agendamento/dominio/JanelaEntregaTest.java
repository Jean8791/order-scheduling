package br.com.estudos.agendamento.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JanelaEntregaTest {

    @Test
    void novaJanelaComecaVazia() {
        JanelaEntrega janela = JanelaEntrega.nova("SP", LocalDate.of(2026, 12, 10), Periodo.MANHA, 5, Instant.EPOCH);

        assertThat(janela.getId()).isNotBlank();
        assertThat(janela.getOcupados()).isZero();
        assertThat(janela.disponiveis()).isEqualTo(5);
    }

    @Test
    void capacidadeTemQueSerPositiva() {
        assertThatThrownBy(() -> JanelaEntrega.nova("SP", LocalDate.of(2026, 12, 10), Periodo.MANHA, 0, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
