package br.com.estudos.auth.entity;

/**
 * Papel do usuário. Vai dentro do token, na claim {@code roles}, e os outros
 * serviços o transformam em autoridade do Spring Security ({@code ROLE_ADMIN},
 * {@code ROLE_USER}) para usar em {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
public enum Role {
    USER,
    ADMIN
}
