package br.com.estudos.scheduling.exception;

import br.com.estudos.scheduling.dto.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode() != null ? ex.getCode() : ErrorCode.BAD_REQUEST;
        return build(ex.getStatus(), code, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return build(400, ErrorCode.BAD_REQUEST, "Falha de validação", request, fields);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error. correlationId={} path={}", correlationId(request), path(request), ex);
        return build(500, ErrorCode.INTERNAL_ERROR, "Erro interno inesperado", request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage(),
                        error.getRejectedValue() == null ? null : String.valueOf(error.getRejectedValue())))
                .toList();
        return build(400, ErrorCode.BAD_REQUEST, "Falha de validação", servletRequest(request), fields);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        int status = statusCode.value();
        return build(status, ErrorCode.fromStatus(status), message(status), servletRequest(request), List.of());
    }

    private ResponseEntity<Object> build(int status, ErrorCode code, String message, HttpServletRequest request,
                                         List<ErrorResponse.FieldError> fields) {
        ErrorResponse body = ErrorResponse.of(status, reasonPhrase(status), code, message, path(request),
                correlationId(request), fields);
        return ResponseEntity.status(status).body(body);
    }

    private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> v) {
        String field = v.getPropertyPath() != null ? v.getPropertyPath().toString() : null;
        Object invalid = v.getInvalidValue();
        return new ErrorResponse.FieldError(field, v.getMessage(), invalid == null ? null : String.valueOf(invalid));
    }

    private String message(int status) {
        return switch (status) {
            case 400 -> "Requisição inválida";
            case 404 -> "Recurso não encontrado";
            case 405 -> "Método não suportado";
            case 415 -> "Tipo de conteúdo não suportado";
            default -> status >= 500 ? "Erro interno inesperado" : reasonPhrase(status);
        };
    }

    private String reasonPhrase(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return httpStatus != null ? httpStatus.getReasonPhrase() : "Error";
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest() : null;
    }

    private String path(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String query = request.getQueryString();
        return query == null || query.isBlank() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String correlationId(HttpServletRequest request) {
        Object cid = request != null ? request.getAttribute(CorrelationIdFilter.PROP) : null;
        return cid != null ? cid.toString() : null;
    }
}
