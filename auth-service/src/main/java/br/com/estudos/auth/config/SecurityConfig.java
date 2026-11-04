package br.com.estudos.auth.config;

import br.com.estudos.auth.exception.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        .anyRequest().authenticated())
                // Resource server: lê o "Authorization: Bearer <token>", valida o JWT
                // com o JwtDecoder (JwtConfig) e coloca o usuário no SecurityContext.
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    /** BCrypt: hash lento e com salt, feito para guardar senhas. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
