package br.com.estudos.agendamento.api.dto;

import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.dominio.Periodo;

import java.time.LocalDate;

public record JanelaResponse(String id, String uf, LocalDate data, Periodo periodo, int capacidade, int ocupados, int disponiveis) {

    public static JanelaResponse de(JanelaEntrega janela) {
        return new JanelaResponse(janela.getId(), janela.getUf(), janela.getData(), janela.getPeriodo(),
                janela.getCapacidade(), janela.getOcupados(), janela.disponiveis());
    }
}
