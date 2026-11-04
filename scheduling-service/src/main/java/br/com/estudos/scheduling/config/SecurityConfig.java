package br.com.estudos.scheduling.config;

import br.com.estudos.scheduling.exception.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração do Spring Security.
 *
 * <p>O {@link SecurityFilterChain} é a lista de filtros por onde toda
 * requisição passa antes de chegar ao controller. Aqui ela diz quais rotas são
 * públicas, que a API não guarda sessão (cada requisição traz o próprio token)
 * e como responder 401/403.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) throws Exception {
        return http
                // CSRF protege sessões baseadas em cookie. Aqui o token vai no header
                // Authorization e o navegador não o envia sozinho, então não se aplica.
                .csrf(AbstractHttpConfigurer::disable)
                // Sem sessão no servidor: cada requisição se autentica pelo próprio token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Regras por rota, conferidas na ordem: a primeira que casar vale.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // Resource server: lê o "Authorization: Bearer <token>", valida o JWT
                // com o JwtDecoder (JwtConfig) e coloca o usuário no SecurityContext.
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken()))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    /**
     * Diz ao Spring Security de onde tirar as permissões do usuário: da claim
     * {@code roles} do token, com o prefixo {@code ROLE_}. Um token com
     * {@code "roles": ["ADMIN"]} vira a autoridade {@code ROLE_ADMIN}, que é o que
     * {@code hasRole('ADMIN')} confere.
     */
    private static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
