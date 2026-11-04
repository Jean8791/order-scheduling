package br.com.estudos.scheduling.exception;

import br.com.estudos.scheduling.dto.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Respostas de 401 e 403 do Spring Security.
 *
 * <p>Erros de autenticação acontecem nos filtros, antes do controller, então o
 * {@code GlobalExceptionHandler} não os vê. {@code AuthenticationEntryPoint}
 * (sem token ou token inválido = 401) e {@code AccessDeniedHandler} (sem
 * permissão = 403) escrevem o mesmo formato de erro do resto da API.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, ErrorCode.UNAUTHORIZED, "Token ausente, inválido ou expirado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN, "Acesso negado");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) throws IOException {
        int status = code.getHttpStatus();
        Object correlationId = request.getAttribute(CorrelationIdFilter.PROP);
        ErrorResponse body = ErrorResponse.of(status, HttpStatus.valueOf(status).getReasonPhrase(), code, message,
                request.getRequestURI(), correlationId != null ? correlationId.toString() : null, List.of());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
