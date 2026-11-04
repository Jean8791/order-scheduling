package br.com.estudos.auth.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /auth/register}. A senha tem no máximo 72 caracteres
 * porque o BCrypt ignora o que passar disso.
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
