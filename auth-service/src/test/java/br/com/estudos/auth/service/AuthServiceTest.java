package br.com.estudos.auth.service;

import br.com.estudos.auth.dto.auth.LoginRequest;
import br.com.estudos.auth.dto.auth.LoginResponse;
import br.com.estudos.auth.entity.User;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.mapper.UserMapper;
import br.com.estudos.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Mock
    UserRepository repository;

    @Mock
    TokenService tokenService;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(repository, passwordEncoder, tokenService, new UserMapper());
        user = User.create("Ana", "ana@email.com", passwordEncoder.encode("senha-forte"), NOW);
    }

    @Test
    void validCredentialsReturnToken() {
        Jwt token = Jwt.withTokenValue("token-assinado").header("alg", "RS256").subject("ana@email.com")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)).build();
        when(repository.findById("ana@email.com")).thenReturn(Optional.of(user));
        when(tokenService.generate(user)).thenReturn(token);

        LoginResponse response = service.login(new LoginRequest(" ANA@email.com ", "senha-forte"));

        assertThat(response.token()).isEqualTo("token-assinado");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(response.user().email()).isEqualTo("ana@email.com");
    }

    @Test
    void wrongPasswordReturnsUnauthorized() {
        when(repository.findById("ana@email.com")).thenReturn(Optional.of(user));

        assertInvalidCredentials(new LoginRequest("ana@email.com", "senha-errada"));
    }

    @Test
    void unknownEmailReturnsSameUnauthorizedMessage() {
        when(repository.findById("ninguem@email.com")).thenReturn(Optional.empty());

        assertInvalidCredentials(new LoginRequest("ninguem@email.com", "senha-forte"));
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("E-mail ou senha inválidos");
                });
        verifyNoInteractions(tokenService);
    }
}
