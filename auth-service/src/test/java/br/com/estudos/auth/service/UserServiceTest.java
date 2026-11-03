package br.com.estudos.auth.service;

import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.dto.user.UserResponse;
import br.com.estudos.auth.entity.User;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.mapper.UserMapper;
import br.com.estudos.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Mock
    UserRepository repository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, passwordEncoder, new UserMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersUserWithNormalizedEmailAndHashedPassword() {
        UserResponse response = service.register(new UserCreateRequest("  Ana Souza ", " Ana@Email.COM ", "senha-forte"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).insert(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@email.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana Souza");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("senha-forte");
        assertThat(passwordEncoder.matches("senha-forte", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.email()).isEqualTo("ana@email.com");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void duplicatedEmailReturnsConflict() {
        when(repository.insert(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() -> service.register(new UserCreateRequest("Ana", "ana@email.com", "senha-forte")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("E-mail já cadastrado");
                });
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        when(repository.findById("ana@email.com")).thenReturn(Optional.of(User.create("Ana", "ana@email.com", "hash", NOW)));

        assertThat(service.findByEmail("ANA@email.com").name()).isEqualTo("Ana");
    }

    @Test
    void unknownUserReturnsNotFound() {
        when(repository.findById("ana@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail("ana@email.com"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
