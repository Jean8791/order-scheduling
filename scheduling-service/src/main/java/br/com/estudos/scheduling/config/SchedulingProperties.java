package br.com.estudos.scheduling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "scheduling")
public record SchedulingProperties(int searchDays, ZoneId zone) {

    public SchedulingProperties {
        if (searchDays < 0) {
            throw new IllegalArgumentException("scheduling.search-days não pode ser negativo");
        }
        if (zone == null) {
            zone = ZoneId.of("America/Sao_Paulo");
        }
    }
}
