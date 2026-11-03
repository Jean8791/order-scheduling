package br.com.estudos.pedido.mensageria;

import java.time.Instant;
import java.time.LocalDate;

public final class Eventos {

    private Eventos() {
    }

    public record PedidoCriado(String eventoId, Instant ocorridoEm, String pedidoId, String cep, String uf,
                               LocalDate dataEntregaDesejada, int quantidadeItens) {
    }

    public record PedidoCancelado(String eventoId, Instant ocorridoEm, String pedidoId, String motivo) {
    }

    public record AgendamentoConfirmado(String eventoId, Instant ocorridoEm, String pedidoId, String janelaId,
                                        LocalDate data, String periodo) {
    }

    public record AgendamentoRecusado(String eventoId, Instant ocorridoEm, String pedidoId, String motivo) {
    }
}
