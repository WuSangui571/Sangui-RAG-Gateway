package com.sangui.raggateway.gateway.openai;

import java.util.Collections;
import java.util.List;

public class OpenAiModelsResponse {

    private final String object;
    private final List<OpenAiModel> data;

    private OpenAiModelsResponse(List<OpenAiModel> data) {
        this.object = "list";
        this.data = data;
    }

    public static OpenAiModelsResponse of(OpenAiModel model) {
        return new OpenAiModelsResponse(Collections.singletonList(model));
    }

    public String getObject() {
        return object;
    }

    public List<OpenAiModel> getData() {
        return data;
    }
}
