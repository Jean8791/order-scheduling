package br.com.estudos.auth.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Usuário gravado na coleção {@code users} do MongoDB.
 *
 * <p>{@code @Document} indica a coleção e {@code @Id} o campo usado como
 * {@code _id} (aqui, o e-mail). O construtor vazio protegido é para o Spring
 * Data montar o objeto ao ler do banco.
 */
@Document("users")
public class User {

    @Id
    private String email;
    private String name;
    private String passwordHash;
    private Instant createdAt;
    private Role role;

    protected User() {
    }

    public static User create(String name, String email, String passwordHash, Instant now) {
        User user = new User();
        user.name = name;
        user.email = email;
        user.passwordHash = passwordHash;
        user.createdAt = now;
        user.role = Role.USER;
        return user;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Usuários gravados antes de existir o campo {@code role} são tratados como USER. */
    public Role getRole() {
        return role == null ? Role.USER : role;
    }

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }
}
