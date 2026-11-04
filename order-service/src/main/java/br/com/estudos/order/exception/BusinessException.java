package br.com.estudos.order.exception;

/**
 * Erro de regra de negócio com o status HTTP que ele deve gerar.
 *
 * <p>Os services lançam esta exceção e o {@code GlobalExceptionHandler} a
 * transforma na resposta JSON de erro.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final int status;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.status = code.getHttpStatus();
    }

    public ErrorCode getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException of(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }
}
