package com.sangui.raggateway.gateway.openai;

import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.gateway.completion.ChatCompletionGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test")
public class OpenAiChatCompletionsController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatCompletionsController.class);

    private final ChatCompletionGatewayService chatCompletionGatewayService;

    public OpenAiChatCompletionsController(ChatCompletionGatewayService chatCompletionGatewayService) {
        this.chatCompletionGatewayService = chatCompletionGatewayService;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<OpenAiChatCompletionResponse> chatCompletions(
            @RequestBody OpenAiChatCompletionRequest request) {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        if (context == null) {
            throw new GatewayException("Invalid API key.", "invalid_request_error", "invalid_api_key", HttpStatus.UNAUTHORIZED);
        }

        log.info("Received chat completions request: apiKeyId={}, appId={}", context.getApiKeyId(), context.getAppId());
        OpenAiChatCompletionResponse response = chatCompletionGatewayService.processChatCompletion(request);
        return ResponseEntity.ok(response);
    }
}
