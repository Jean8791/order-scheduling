package br.com.estudos.agendamento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "agendamento")
public record AgendamentoProperties(int diasBusca, ZoneId zona) {

    public AgendamentoProperties {
        if (diasBusca < 0) {
            throw new IllegalArgumentException("agendamento.dias-busca não pode ser negativo");
        }
        if (zona == null) {
            zona = ZoneId.of("America/Sao_Paulo");
        }
    }
}
