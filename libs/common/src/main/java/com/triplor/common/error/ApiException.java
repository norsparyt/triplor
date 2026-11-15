package com.triplor.common.error;

import org.springframework.http.HttpStatus;

/**
 * A failure the caller can act on. It carries the HTTP status and a stable machine-readable code;
 * {@link ApiExceptionHandler} turns it into an RFC 9457 problem response.
 *
 * <p>Clients branch on {@code code}, never on the human-readable detail.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
