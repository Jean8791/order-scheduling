package br.com.estudos.auth.dto.user;

import br.com.estudos.auth.entity.Role;

import java.time.Instant;

public record UserResponse(String name, String email, Role role, Instant createdAt) {
}
