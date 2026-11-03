package br.com.estudos.agendamento.dominio;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Document("janelas")
public class JanelaEntrega {

    @Id
    private String id;
    private String uf;
    private LocalDate data;
    private Periodo periodo;
    private int capacidade;
    private int ocupados;
    private Instant criadoEm;

    protected JanelaEntrega() {
    }

    public static JanelaEntrega nova(String uf, LocalDate data, Periodo periodo, int capacidade, Instant agora) {
        if (capacidade < 1) {
            throw new IllegalArgumentException("A capacidade da janela deve ser maior que zero");
        }
        JanelaEntrega janela = new JanelaEntrega();
        janela.id = UUID.randomUUID().toString();
        janela.uf = uf;
        janela.data = data;
        janela.periodo = periodo;
        janela.capacidade = capacidade;
        janela.ocupados = 0;
        janela.criadoEm = agora;
        return janela;
    }

    public int disponiveis() {
        return Math.max(0, capacidade - ocupados);
    }

    public String getId() {
        return id;
    }

    public String getUf() {
        return uf;
    }

    public LocalDate getData() {
        return data;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public int getOcupados() {
        return ocupados;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
