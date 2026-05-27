package com.sangui.raggateway.common.response;

public class OpenAiErrorResponse {

    private final OpenAiError error;

    private OpenAiErrorResponse(OpenAiError error) {
        this.error = error;
    }

    public static OpenAiErrorResponse of(String message, String type, String code) {
        return new OpenAiErrorResponse(OpenAiError.of(message, type, code));
    }

    public OpenAiError getError() {
        return error;
    }
}
