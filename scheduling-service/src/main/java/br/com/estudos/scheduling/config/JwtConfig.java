package br.com.estudos.scheduling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

/**
 * Beans que assinam e validam o token JWT.
 *
 * <p>{@link org.springframework.security.oauth2.jwt.JwtEncoder} assina com a
 * chave privada (só o auth-service tem). {@link
 * org.springframework.security.oauth2.jwt.JwtDecoder} confere a assinatura com
 * a chave pública e valida emissor e expiração. É o algoritmo RS256: quem só
 * valida token nunca precisa da chave privada.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(properties)).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static RSAPublicKey publicKey(JwtProperties properties) {
        try (InputStream in = properties.publicKey().getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new IllegalStateException("Chave JWT não encontrada em " + properties.publicKey() + ". Gere as chaves com scripts/generate-keys.sh", e);
        }
    }
}
