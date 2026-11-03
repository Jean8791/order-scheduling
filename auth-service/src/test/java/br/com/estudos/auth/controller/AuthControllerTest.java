package br.com.estudos.auth.controller;

import br.com.estudos.auth.config.SecurityConfig;
import br.com.estudos.auth.dto.auth.LoginResponse;
import br.com.estudos.auth.dto.user.UserResponse;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.exception.SecurityErrorHandler;
import br.com.estudos.auth.service.AuthService;
import br.com.estudos.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final UserResponse ANA = new UserResponse("Ana", "ana@email.com", NOW);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void registerIsPublicAndReturns201() throws Exception {
        when(userService.register(any())).thenReturn(ANA);

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@email.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@email.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void invalidRegisterReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nao-e-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));

        verifyNoInteractions(userService);
    }

    @Test
    void duplicatedEmailReturns409() throws Exception {
        when(userService.register(any())).thenThrow(BusinessException.of(ErrorCode.CONFLICT, "E-mail já cadastrado"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@email.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado"));
    }

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-assinado", "Bearer", NOW.plusSeconds(3600), ANA));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@email.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-assinado"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.name").value("Ana"));
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        when(authService.login(any())).thenThrow(BusinessException.of(ErrorCode.UNAUTHORIZED, "E-mail ou senha inválidos"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@email.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token ausente, inválido ou expirado"));

        verifyNoInteractions(userService);
    }

    @Test
    void meReturnsTheTokenOwner() throws Exception {
        when(userService.findByEmail("ana@email.com")).thenReturn(ANA);

        mvc.perform(get("/auth/me").with(jwt().jwt(token -> token.subject("ana@email.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"));
    }
}
