package com.sangui.raggateway.retrieval.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.retrieval.Citation;
import com.sangui.raggateway.retrieval.RetrievalResult;
import com.sangui.raggateway.retrieval.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalEvaluationServiceTest {

    @Mock
    private AppService appService;

    @Mock
    private RetrievalService retrievalService;

    private RetrievalEvaluationService service;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long KB_ID = 20L;

    @BeforeEach
    void setUp() {
        service = new RetrievalEvaluationService(appService, retrievalService, new ObjectMapper());
    }

    private AppEntity createApp() {
        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setStatus("ENABLED");
        app.setDefaultKnowledgeBaseId(KB_ID);
        app.setRetrievalTopK(5);
        app.setRetrievalSimilarityThreshold(0.3);
        app.setRetrievalMaxContextChunks(5);
        app.setRetrievalMaxContextChars(12000);
        app.setRetrievalMaxSingleChunkChars(3000);
        return app;
    }

    private KnowledgeBaseEntity createReadyKb() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(KB_ID);
        kb.setUserId(USER_ID);
        kb.setStatus("READY");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setEmbeddingDimension(1536);
        return kb;
    }

    private RetrievalResult createHitResult(List<Long> chunkIds, List<Long> docIds, String filename, double similarity) {
        List<Citation> citations = new java.util.ArrayList<>();
        List<RetrievalResult.RetrievedChunk> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < chunkIds.size(); i++) {
            Long chunkId = chunkIds.get(i);
            Long docId = docIds.get(i);
            citations.add(new Citation("S" + (i + 1), chunkId, docId, KB_ID, filename, i, similarity, null, 100, 100));
            chunks.add(new RetrievalResult.RetrievedChunk(chunkId, docId, KB_ID, i, filename, "content", null, similarity, 100, 100, "S" + (i + 1)));
        }
        return new RetrievalResult(chunks, chunkIds, citations, null, false, 50L);
    }

    @Test
    void shouldComputeMetricsForMatchingCase() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createApp());
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(createReadyKb());
        when(retrievalService.retrieve(eq("Sangui gateway default retrieval top k"), any(), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt()))
                .thenReturn(createHitResult(List.of(8L), List.of(4L), "handbook.md", 0.85));

        RetrievalEvaluationRunResult result = service.run(APP_ID, USER_ID, List.of("case-001"), 1);

        assertThat(result.getCaseCount()).isEqualTo(1);
        assertThat(result.getKnowledgeBaseId()).isEqualTo(KB_ID);
        RetrievalEvaluationCaseResult caseResult = result.getCases().get(0);
        assertThat(caseResult.getActualChunkIds()).containsExactly(8L);
        assertThat(caseResult.isHit()).isTrue();
        assertThat(caseResult.getRank()).isEqualTo(1);
        assertThat(caseResult.getPrecisionAtK()).isEqualTo(1.0);
        assertThat(caseResult.getRecallAtK()).isEqualTo(1.0);
        assertThat(caseResult.getMrr()).isEqualTo(1.0);
        assertThat(result.getHitCount()).isEqualTo(1);
    }

    @Test
    void shouldReportNoHitsCaseAsHitWhenRetrievalEmpty() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createApp());
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(createReadyKb());
        when(retrievalService.retrieve(anyString(), any(), anyInt(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new RetrievalResult(List.of(), List.of(), List.of(), null, true, 50L));

        RetrievalEvaluationRunResult result = service.run(APP_ID, USER_ID, List.of("case-003"), 1);

        RetrievalEvaluationCaseResult caseResult = result.getCases().get(0);
        assertThat(caseResult.isNoHits()).isTrue();
        assertThat(caseResult.isHit()).isTrue();
        assertThat(caseResult.getActualChunkIds()).isEmpty();
    }

    @Test
    void shouldReportErrorCodeWhenRetrievalThrows() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createApp());
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(createReadyKb());
        when(retrievalService.retrieve(anyString(), any(), anyInt(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("embedding down"));

        RetrievalEvaluationRunResult result = service.run(APP_ID, USER_ID, List.of("case-001"), 1);

        RetrievalEvaluationCaseResult caseResult = result.getCases().get(0);
        assertThat(caseResult.isHit()).isFalse();
        assertThat(caseResult.getErrorCode()).isEqualTo("retrieval_error");
        assertThat(caseResult.getActualChunkIds()).isEmpty();
        assertThat(result.getHitCount()).isZero();
    }

    @Test
    void shouldRejectWhenKnowledgeBaseNotReady() {
        AppEntity app = createApp();
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(app);
        KnowledgeBaseEntity kb = createReadyKb();
        kb.setStatus("EMPTY");
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(kb);

        assertThatThrownBy(() -> service.run(APP_ID, USER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .matches(e -> "KNOWLEDGE_BASE_NOT_READY".equals(((BusinessException) e).getCode()));
    }

    @Test
    void shouldRejectWhenAppNotFound() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.run(APP_ID, USER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .matches(e -> "NOT_FOUND".equals(((BusinessException) e).getCode()));
    }

    @Test
    void shouldRejectEmptyCaseSelection() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createApp());
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(createReadyKb());

        assertThatThrownBy(() -> service.run(APP_ID, USER_ID, List.of("nonexistent-case"), null))
                .isInstanceOf(BusinessException.class)
                .matches(e -> "INVALID_REQUEST".equals(((BusinessException) e).getCode()));
    }

    @Test
    void shouldValidateLimitBounds() {
        assertThatThrownBy(() -> service.validateLimit(0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validateLimit(101))
                .isInstanceOf(BusinessException.class);
        service.validateLimit(50);
        service.validateLimit(null);
    }

    @Test
    void shouldNotExposeForbiddenFieldsInCaseResult() {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createApp());
        when(appService.resolveDefaultKnowledgeBase(any())).thenReturn(createReadyKb());
        when(retrievalService.retrieve(anyString(), any(), anyInt(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .thenReturn(createHitResult(List.of(8L), List.of(4L), "handbook.md", 0.85));

        RetrievalEvaluationRunResult result = service.run(APP_ID, USER_ID, List.of("case-001"), 1);
        String json = new ObjectMapper().valueToTree(result).toString();

        assertThat(json).doesNotContain("content").doesNotContain("prompt")
                .doesNotContain("embedding").doesNotContain("api_key")
                .doesNotContain("storage_path").doesNotContain("provider_response_body");
    }
}
