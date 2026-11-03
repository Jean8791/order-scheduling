package br.com.estudos.agendamento.api.dto;

import br.com.estudos.agendamento.dominio.Agendamento;
import br.com.estudos.agendamento.dominio.Periodo;
import br.com.estudos.agendamento.dominio.StatusAgendamento;

import java.time.Instant;
import java.time.LocalDate;

public record AgendamentoResponse(String pedidoId, StatusAgendamento status, String janelaId, LocalDate data,
                                  Periodo periodo, String motivo, Instant criadoEm, Instant atualizadoEm) {

    public static AgendamentoResponse de(Agendamento agendamento) {
        return new AgendamentoResponse(agendamento.getPedidoId(), agendamento.getStatus(), agendamento.getJanelaId(),
                agendamento.getData(), agendamento.getPeriodo(), agendamento.getMotivo(),
                agendamento.getCriadoEm(), agendamento.getAtualizadoEm());
    }
}
