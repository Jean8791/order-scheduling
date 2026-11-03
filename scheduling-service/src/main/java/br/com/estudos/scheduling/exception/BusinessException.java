package br.com.estudos.scheduling.exception;

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
