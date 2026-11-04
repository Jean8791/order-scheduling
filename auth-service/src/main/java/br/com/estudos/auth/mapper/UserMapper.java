package br.com.estudos.auth.mapper;

import br.com.estudos.auth.dto.auth.LoginResponse;
import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.dto.user.UserResponse;
import br.com.estudos.auth.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * Converte entre DTOs (o que trafega na API) e a entidade {@code User}.
 */
@Component
public class UserMapper {

    public User toEntity(UserCreateRequest req, String passwordHash, Instant now) {
        return User.create(req.name().trim(), normalizeEmail(req.email()), passwordHash, now);
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }

    public LoginResponse toLoginResponse(User user, Jwt token) {
        return new LoginResponse(token.getTokenValue(), "Bearer", token.getExpiresAt(), toResponse(user));
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
