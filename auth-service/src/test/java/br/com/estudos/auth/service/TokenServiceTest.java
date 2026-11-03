package br.com.estudos.auth.service;

import br.com.estudos.auth.config.JwtProperties;
import br.com.estudos.auth.entity.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private RSAPublicKey publicKey;
    private TokenService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair pair = rsaKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        RSAKey key = new RSAKey.Builder(publicKey).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        service = new TokenService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))),
                new JwtProperties("auth-service", Duration.ofHours(6), null, null), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void generatesSignedTokenWithUserClaims() {
        Jwt token = service.generate(User.create("Ana", "ana@email.com", "hash", now));

        Jwt decoded = decoder(publicKey, "auth-service").decode(token.getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("ana@email.com");
        assertThat(decoded.getClaimAsString("name")).isEqualTo("Ana");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("auth-service");
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(6)));
    }

    @Test
    void tokenIsRejectedWithAnotherPublicKey() throws Exception {
        Jwt token = service.generate(User.create("Ana", "ana@email.com", "hash", now));
        RSAPublicKey otherKey = (RSAPublicKey) rsaKeyPair().getPublic();

        assertThatThrownBy(() -> decoder(otherKey, "auth-service").decode(token.getTokenValue()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenIsRejectedWithAnotherIssuer() {
        Jwt token = service.generate(User.create("Ana", "ana@email.com", "hash", now));

        assertThatThrownBy(() -> decoder(publicKey, "outro-emissor").decode(token.getTokenValue()))
                .isInstanceOf(JwtException.class);
    }

    private static NimbusJwtDecoder decoder(RSAPublicKey key, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
