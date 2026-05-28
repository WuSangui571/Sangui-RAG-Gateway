package com.sangui.raggateway.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ApiRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogService.class);

    private final ApiRequestLogMapper apiRequestLogMapper;

    public ApiRequestLogService(ApiRequestLogMapper apiRequestLogMapper) {
        this.apiRequestLogMapper = apiRequestLogMapper;
    }

    public void record(CreateRequestLogCommand command) {
        try {
            ApiRequestLogEntity entity = toEntity(command);
            apiRequestLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to persist request log for request_id={}, errorType={}",
                    command.getRequestId(), e.getClass().getSimpleName());
        }
    }

    private ApiRequestLogEntity toEntity(CreateRequestLogCommand command) {
        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setRequestId(command.getRequestId());
        entity.setUserId(command.getUserId());
        entity.setAppId(command.getAppId());
        entity.setApiKeyId(command.getApiKeyId());
        entity.setModel(command.getModel());
        entity.setProviderName(command.getProviderName());
        entity.setStatus(command.getStatus());
        entity.setErrorCode(command.getErrorCode());
        entity.setLatencyMs(command.getLatencyMs());
        entity.setUpstreamLatencyMs(command.getUpstreamLatencyMs());
        entity.setPromptTokens(command.getPromptTokens());
        entity.setCompletionTokens(command.getCompletionTokens());
        entity.setTotalTokens(command.getTotalTokens());
        entity.setMessagesCount(command.getMessagesCount());
        entity.setQuestionSummary(command.getQuestionSummary());
        entity.setHitChunkIds(command.getHitChunkIds());
        return entity;
    }
}
