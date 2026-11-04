package br.com.estudos.scheduling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expõe um {@link java.time.Clock} como bean.
 *
 * <p>As classes pedem o relógio pelo construtor em vez de chamar {@code
 * Instant.now()} direto. Em produção é o relógio do sistema; nos testes dá
 * para passar um {@code Clock.fixed(...)} e controlar a data.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
