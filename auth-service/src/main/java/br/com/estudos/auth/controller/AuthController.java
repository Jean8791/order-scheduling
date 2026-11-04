package br.com.estudos.auth.controller;

import br.com.estudos.auth.dto.auth.LoginRequest;
import br.com.estudos.auth.dto.auth.LoginResponse;
import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.dto.user.UserResponse;
import br.com.estudos.auth.service.AuthService;
import br.com.estudos.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de cadastro e login (Spring MVC).
 *
 * <p>{@code @RestController} = controller cujo retorno vira JSON no corpo da
 * resposta. {@code @RequestMapping} define o prefixo das rotas. {@code @Valid}
 * dispara as validações do DTO ({@code @NotBlank}, {@code @Email}...); se
 * falharem, o {@code GlobalExceptionHandler} responde 400.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Cadastro de usuário, login e consulta do usuário logado")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Cadastra um usuário com nome, e-mail e senha")
    public UserResponse register(@Valid @RequestBody UserCreateRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica com e-mail e senha e devolve o token de acesso")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Consulta o usuário dono do token")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.findByEmail(jwt.getSubject());
    }
}
