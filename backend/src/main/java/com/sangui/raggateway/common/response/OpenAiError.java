package com.sangui.raggateway.common.response;

public class OpenAiError {

    private final String message;
    private final String type;
    private final String code;

    private OpenAiError(String message, String type, String code) {
        this.message = message;
        this.type = type;
        this.code = code;
    }

    public static OpenAiError of(String message, String type, String code) {
        return new OpenAiError(message, type, code);
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }
}
