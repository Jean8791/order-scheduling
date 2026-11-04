package br.com.estudos.auth.dto.auth;

import br.com.estudos.auth.dto.user.UserResponse;

import java.time.Instant;

/**
 * Resposta do login: o token, o tipo ({@code Bearer}), quando expira e os
 * dados do usuário.
 */
public record LoginResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
