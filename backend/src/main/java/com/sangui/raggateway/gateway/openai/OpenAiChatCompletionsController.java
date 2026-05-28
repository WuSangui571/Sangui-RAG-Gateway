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

import java.util.UUID;

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

        String requestId = UUID.randomUUID().toString();
        context.setRequestId(requestId);

        int messagesCount = request != null && request.getMessages() != null ? request.getMessages().size() : 0;
        long start = System.currentTimeMillis();

        try {
            OpenAiChatCompletionResponse response = chatCompletionGatewayService.processChatCompletion(request);
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=success messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    messagesCount, latencyMs);
            return ResponseEntity.ok(response);
        } catch (GatewayException e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    e.getCode(), messagesCount, latencyMs);
            throw e;
        }
    }
}
