package br.com.estudos.auth.dto.user;

import java.time.Instant;

public record UserResponse(String name, String email, Instant createdAt) {
}
