package br.com.estudos.auth.exception;

public enum ErrorCode {

    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    UNPROCESSABLE(422),
    TOO_MANY_REQUESTS(429),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static ErrorCode fromStatus(int status) {
        for (ErrorCode code : values()) {
            if (code.httpStatus == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
    }
}
