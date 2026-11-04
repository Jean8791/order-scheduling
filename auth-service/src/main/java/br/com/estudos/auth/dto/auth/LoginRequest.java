package br.com.estudos.auth.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /auth/login}. As anotações do Bean Validation são
 * conferidas pelo {@code @Valid} do controller.
 */
public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
}
