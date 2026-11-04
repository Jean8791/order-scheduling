package br.com.estudos.scheduling.dto.exception;

import br.com.estudos.scheduling.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Formato padrão de erro devolvido por toda a API.
 */
public record ErrorResponse(String timestamp, int status, String error, ErrorCode code, String message, String path,
                            String correlationId, List<FieldError> fieldErrors) {

    public static ErrorResponse of(int status, String error, ErrorCode code, String message, String path,
                                   String correlationId, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now().toString(), status, error, code, message, path, correlationId, fieldErrors);
    }

    public record FieldError(String field, String message, Object rejectedValue) {
    }
}
