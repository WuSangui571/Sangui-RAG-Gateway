package com.sangui.raggateway.gateway.openai;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.model.ModelConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test")
public class OpenAiModelsController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiModelsController.class);

    private static final String ERR_TYPE = "invalid_request_error";
    private static final String ERR_CODE = "model_config_not_ready";
    private static final String ERR_MESSAGE = "Default model config is not configured for this app.";

    private final AppService appService;

    public OpenAiModelsController(AppService appService) {
        this.appService = appService;
    }

    @GetMapping("/v1/models")
    public ResponseEntity<OpenAiModelsResponse> listModels() {
        GatewayRequestContext context = GatewayRequestContextHolder.get();

        AppEntity app = appService.findById(context.getAppId());
        if (app == null) {
            log.warn("App not found for appId={}", context.getAppId());
            throw new GatewayException(ERR_MESSAGE, ERR_TYPE, ERR_CODE, HttpStatus.CONFLICT);
        }

        ModelConfigEntity modelConfig = appService.resolveDefaultModelConfig(app);
        if (modelConfig == null) {
            log.warn("Default model config not ready for appId={}", app.getId());
            throw new GatewayException(ERR_MESSAGE, ERR_TYPE, ERR_CODE, HttpStatus.CONFLICT);
        }

        OpenAiModel model = new OpenAiModel(modelConfig.getChatModel(), modelConfig.getProviderName());
        return ResponseEntity.ok(OpenAiModelsResponse.of(model));
    }
}
