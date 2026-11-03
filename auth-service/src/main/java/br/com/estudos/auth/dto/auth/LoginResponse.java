package br.com.estudos.auth.dto.auth;

import br.com.estudos.auth.dto.user.UserResponse;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
