package br.com.estudos.auth.dto.user;

import br.com.estudos.auth.entity.Role;

import java.time.Instant;

/**
 * Dados públicos do usuário. Nunca inclui o hash da senha.
 */
public record UserResponse(String name, String email, Role role, Instant createdAt) {
}
