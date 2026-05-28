package com.sangui.raggateway.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class EmbeddingRequest {

    private String model;
    @JsonProperty("input")
    private List<String> input;

    public EmbeddingRequest() {
    }

    public EmbeddingRequest(String model, List<String> input) {
        this.model = model;
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }
}
