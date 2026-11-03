package br.com.estudos.pedido.dominio;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Document("pedidos")
public class Pedido {

    @Id
    private String id;
    private String cliente;
    private Endereco endereco;
    private List<ItemPedido> itens;
    private LocalDate dataEntregaDesejada;
    private StatusPedido status;
    private AgendamentoPedido agendamento;
    private String motivo;
    private Instant criadoEm;
    private Instant atualizadoEm;

    @Version
    private Long versao;

    protected Pedido() {
    }

    public static Pedido novo(String cliente, Endereco endereco, List<ItemPedido> itens, LocalDate dataEntregaDesejada, Instant agora) {
        Pedido pedido = new Pedido();
        pedido.id = UUID.randomUUID().toString();
        pedido.cliente = cliente;
        pedido.endereco = endereco;
        pedido.itens = List.copyOf(itens);
        pedido.dataEntregaDesejada = dataEntregaDesejada;
        pedido.status = StatusPedido.AGUARDANDO_AGENDAMENTO;
        pedido.criadoEm = agora;
        pedido.atualizadoEm = agora;
        return pedido;
    }

    public boolean confirmarAgendamento(AgendamentoPedido agendamento, Instant agora) {
        if (status != StatusPedido.AGUARDANDO_AGENDAMENTO) {
            return false;
        }
        this.agendamento = agendamento;
        this.status = StatusPedido.AGENDADO;
        this.motivo = null;
        this.atualizadoEm = agora;
        return true;
    }

    public boolean recusarAgendamento(String motivo, Instant agora) {
        if (status != StatusPedido.AGUARDANDO_AGENDAMENTO) {
            return false;
        }
        this.status = StatusPedido.SEM_AGENDA;
        this.motivo = motivo;
        this.atualizadoEm = agora;
        return true;
    }

    public void cancelar(String motivo, Instant agora) {
        if (!status.podeCancelar()) {
            throw new OperacaoInvalidaException("Pedido " + id + " já está cancelado");
        }
        this.status = StatusPedido.CANCELADO;
        this.motivo = motivo;
        this.atualizadoEm = agora;
    }

    public String getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public LocalDate getDataEntregaDesejada() {
        return dataEntregaDesejada;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public AgendamentoPedido getAgendamento() {
        return agendamento;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Long getVersao() {
        return versao;
    }
}
