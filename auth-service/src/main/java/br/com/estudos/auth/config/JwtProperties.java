package br.com.estudos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configurações {@code jwt.*} do application.properties, lidas pelo Spring.
 *
 * <p>{@code @ConfigurationProperties} liga cada propriedade a um campo do
 * record ({@code jwt.public-key} vira {@code publicKey}). O tipo {@code
 * Resource} aceita {@code classpath:...} ou {@code file:...}.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String issuer, Duration expiration, Resource privateKey, Resource publicKey) {
}
