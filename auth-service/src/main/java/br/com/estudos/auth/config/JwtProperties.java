package br.com.estudos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String issuer, Duration expiration, Resource privateKey, Resource publicKey) {
}
