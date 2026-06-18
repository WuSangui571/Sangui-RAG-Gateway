package com.sangui.raggateway.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiRequestLogServiceTest {

    @Mock
    private ApiRequestLogMapper apiRequestLogMapper;

    private ApiRequestLogService service;

    @BeforeEach
    void setUp() {
        service = new ApiRequestLogService(apiRequestLogMapper);
    }

    @Test
    void shouldInsertSuccessRow() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-001")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .model("gpt-4o-mini")
                .providerName("openai")
                .status("success")
                .latencyMs(1234L)
                .upstreamLatencyMs(1100L)
                .promptTokens(10)
                .completionTokens(20)
                .totalTokens(30)
                .messagesCount(2)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getRequestId()).isEqualTo("req-001");
        assertThat(entity.getUserId()).isEqualTo(100L);
        assertThat(entity.getAppId()).isEqualTo(1L);
        assertThat(entity.getApiKeyId()).isEqualTo(30L);
        assertThat(entity.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(entity.getProviderName()).isEqualTo("openai");
        assertThat(entity.getStatus()).isEqualTo("success");
        assertThat(entity.getErrorCode()).isNull();
        assertThat(entity.getLatencyMs()).isEqualTo(1234L);
        assertThat(entity.getUpstreamLatencyMs()).isEqualTo(1100L);
        assertThat(entity.getPromptTokens()).isEqualTo(10);
        assertThat(entity.getCompletionTokens()).isEqualTo(20);
        assertThat(entity.getTotalTokens()).isEqualTo(30);
        assertThat(entity.getMessagesCount()).isEqualTo(2);
    }

    @Test
    void shouldPersistQuestionSummaryAndHitChunkIds() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-rag-001")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .model("deepseek-v4-pro")
                .providerName("sanguicode")
                .status("success")
                .latencyMs(900L)
                .messagesCount(1)
                .questionSummary("手动知识库测试暗号是什么？")
                .hitChunkIds("[8,9]")
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getQuestionSummary()).isEqualTo("手动知识库测试暗号是什么？");
        assertThat(entity.getHitChunkIds()).isEqualTo("[8,9]");
    }

    @Test
    void shouldInsertFailureRow() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-002")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("failure")
                .errorCode("invalid_request")
                .latencyMs(50L)
                .messagesCount(0)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getRequestId()).isEqualTo("req-002");
        assertThat(entity.getStatus()).isEqualTo("failure");
        assertThat(entity.getErrorCode()).isEqualTo("invalid_request");
        assertThat(entity.getModel()).isNull();
        assertThat(entity.getProviderName()).isNull();
        assertThat(entity.getUpstreamLatencyMs()).isNull();
        assertThat(entity.getPromptTokens()).isNull();
        assertThat(entity.getCompletionTokens()).isNull();
        assertThat(entity.getTotalTokens()).isNull();
    }

    @Test
    void shouldNotPersistSensitiveValues() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-003")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .model("gpt-4o-mini")
                .providerName("openai")
                .status("success")
                .latencyMs(100L)
                .messagesCount(1)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        String entityStr = entity.toString();
        assertThat(entityStr).doesNotContain("sk-sangui");
        assertThat(entityStr).doesNotContain("Bearer");
        assertThat(entityStr).doesNotContain("Authorization");
    }

    @Test
    void shouldHandleInsertFailureSafely() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-004")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("success")
                .latencyMs(100L)
                .messagesCount(1)
                .build());

        verify(apiRequestLogMapper).insertRequestLog(any(ApiRequestLogEntity.class));
    }

    @Test
    void shouldPersistUpstreamErrorRow() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-005")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("failure")
                .errorCode("upstream_error")
                .latencyMs(2000L)
                .messagesCount(2)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getErrorCode()).isEqualTo("upstream_error");
        assertThat(entity.getStatus()).isEqualTo("failure");
    }

    @Test
    void shouldPersistUpstreamTimeoutRow() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-006")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("failure")
                .errorCode("upstream_timeout")
                .latencyMs(30000L)
                .messagesCount(2)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getErrorCode()).isEqualTo("upstream_timeout");
        assertThat(entity.getStatus()).isEqualTo("failure");
    }

    @Test
    void shouldPersistModelConfigNotReadyRow() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-007")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("failure")
                .errorCode("model_config_not_ready")
                .latencyMs(10L)
                .messagesCount(2)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getErrorCode()).isEqualTo("model_config_not_ready");
        assertThat(entity.getStatus()).isEqualTo("failure");
    }

    @Test
    void shouldPersistRetrievalEvidenceMetadataOnly() {
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        String evidenceJson = """
                {"version":1,"no_hits":false,"retrieval_latency_ms":42,"top_k":5,
                 "similarity_threshold":0.3,"max_context_chunks":5,
                 "citations":[{"citation_id":"S1","chunk_id":8,"document_id":4,"knowledge_base_id":2,
                 "source_filename":"handbook.md","chunk_index":0,"similarity":0.842,
                 "content_chars":612,"injected_chars":612}]}""";

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-evidence-001")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .model("deepseek-v4-pro")
                .providerName("sanguicode")
                .status("success")
                .latencyMs(900L)
                .messagesCount(1)
                .questionSummary("test")
                .hitChunkIds("[8]")
                .retrievalEvidence(evidenceJson)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getRetrievalEvidence()).isEqualTo(evidenceJson);
        assertThat(entity.getHitChunkIds()).isEqualTo("[8]");
        assertThat(entity.getRetrievalEvidence()).doesNotContain("content\":")
                .doesNotContain("prompt").doesNotContain("embedding")
                .doesNotContain("api_key").doesNotContain("storage_path");
    }
}
