package br.com.estudos.agendamento.dominio;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document("agendamentos")
public class Agendamento {

    @Id
    private String pedidoId;
    private StatusAgendamento status;
    private String janelaId;
    private LocalDate data;
    private Periodo periodo;
    private String motivo;
    private Instant criadoEm;
    private Instant atualizadoEm;

    @Version
    private Long versao;

    protected Agendamento() {
    }

    public static Agendamento processando(String pedidoId, Instant agora) {
        Agendamento agendamento = new Agendamento();
        agendamento.pedidoId = pedidoId;
        agendamento.status = StatusAgendamento.PROCESSANDO;
        agendamento.criadoEm = agora;
        agendamento.atualizadoEm = agora;
        return agendamento;
    }

    public static Agendamento canceladoAntesDeProcessar(String pedidoId, String motivo, Instant agora) {
        Agendamento agendamento = processando(pedidoId, agora);
        agendamento.status = StatusAgendamento.CANCELADO;
        agendamento.motivo = motivo;
        return agendamento;
    }

    public void confirmar(JanelaEntrega janela, Instant agora) {
        this.status = StatusAgendamento.CONFIRMADO;
        this.janelaId = janela.getId();
        this.data = janela.getData();
        this.periodo = janela.getPeriodo();
        this.motivo = null;
        this.atualizadoEm = agora;
    }

    public void recusar(String motivo, Instant agora) {
        this.status = StatusAgendamento.RECUSADO;
        this.motivo = motivo;
        this.atualizadoEm = agora;
    }

    public void cancelar(String motivo, Instant agora) {
        this.status = StatusAgendamento.CANCELADO;
        this.motivo = motivo;
        this.atualizadoEm = agora;
    }

    public boolean ocupaJanela() {
        return status == StatusAgendamento.CONFIRMADO && janelaId != null;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public String getJanelaId() {
        return janelaId;
    }

    public LocalDate getData() {
        return data;
    }

    public Periodo getPeriodo() {
        return periodo;
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
