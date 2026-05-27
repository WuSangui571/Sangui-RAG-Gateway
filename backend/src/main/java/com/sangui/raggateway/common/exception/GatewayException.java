package com.sangui.raggateway.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class GatewayException extends RuntimeException {

    private final String type;
    private final String code;
    private final HttpStatus httpStatus;

    public GatewayException(String message, String type, String code, HttpStatus httpStatus) {
        super(Objects.requireNonNull(message, "message"));
        this.type = Objects.requireNonNull(type, "type");
        this.code = Objects.requireNonNull(code, "code");
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
    }

    public GatewayException(String message, String type, String code, HttpStatus httpStatus, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.type = Objects.requireNonNull(type, "type");
        this.code = Objects.requireNonNull(code, "code");
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
