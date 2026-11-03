package br.com.estudos.agendamento.servico;

import java.time.LocalDate;

public class JanelaDuplicadaException extends RuntimeException {

    public JanelaDuplicadaException(String uf, LocalDate data, String periodo) {
        super("Já existe janela para " + uf + " em " + data + " no período " + periodo);
    }
}
