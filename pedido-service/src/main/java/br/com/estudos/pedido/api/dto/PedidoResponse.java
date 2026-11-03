package br.com.estudos.pedido.api.dto;

import br.com.estudos.pedido.dominio.AgendamentoPedido;
import br.com.estudos.pedido.dominio.Endereco;
import br.com.estudos.pedido.dominio.ItemPedido;
import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.StatusPedido;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PedidoResponse(
        String id,
        String cliente,
        Endereco endereco,
        List<ItemPedido> itens,
        LocalDate dataEntregaDesejada,
        StatusPedido status,
        AgendamentoPedido agendamento,
        String motivo,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(pedido.getId(), pedido.getCliente(), pedido.getEndereco(), pedido.getItens(),
                pedido.getDataEntregaDesejada(), pedido.getStatus(), pedido.getAgendamento(), pedido.getMotivo(),
                pedido.getCriadoEm(), pedido.getAtualizadoEm());
    }
}
