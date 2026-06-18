package com.sangui.raggateway.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private RetrievalMapper retrievalMapper;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private EmbeddingClient embeddingClient;

    private RetrievalService retrievalService;

    private static final Long USER_ID = 100L;
    private static final Long KB_ID = 20L;

    private KnowledgeBaseEntity createReadyKb() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(KB_ID);
        kb.setUserId(USER_ID);
        kb.setName("Test KB");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setEmbeddingDimension(1536);
        kb.setStatus("READY");
        return kb;
    }

    private ModelConfigEntity createEmbeddingConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(5L);
        config.setUserId(USER_ID);
        config.setBaseUrl("https://api.openai.com");
        config.setApiKeyEncrypted("v1:enc:key");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setEmbeddingDimension(1536);
        config.setStatus("ENABLED");
        return config;
    }

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService(retrievalMapper, modelConfigService, embeddingClient, new ObjectMapper());
    }

    @Test
    void shouldRejectNullKnowledgeBase() {
        assertThatThrownBy(() -> retrievalService.retrieve("query", null, 5, 0.7, 5, 12000, 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonReadyKnowledgeBase() {
        KnowledgeBaseEntity kb = createReadyKb();
        kb.setStatus("EMPTY");

        assertThatThrownBy(() -> retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowEmbeddingExceptionWhenNoEmbeddingConfig() {
        KnowledgeBaseEntity kb = createReadyKb();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(null);

        assertThatThrownBy(() -> retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000))
                .isInstanceOf(EmbeddingException.class);
    }

    @Test
    void shouldThrowEmbeddingExceptionWhenUpstreamKeyNull() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        config.setApiKeyEncrypted(null);
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);

        assertThatThrownBy(() -> retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000))
                .isInstanceOf(EmbeddingException.class);
    }

    @Test
    void shouldReturnNoHitsWhenNoRowsReturned() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.isNoHits()).isTrue();
        assertThat(result.getChunks()).isEmpty();
        assertThat(result.getHitChunkIds()).isEmpty();
    }

    @Test
    void shouldFilterBySimilarityThreshold() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow lowSimRow = new ChunkRow();
        lowSimRow.setChunkId(1L);
        lowSimRow.setDocumentId(10L);
        lowSimRow.setContent("low similarity content");
        lowSimRow.setSimilarity(0.5);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(lowSimRow));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.isNoHits()).isTrue();
    }

    @Test
    void shouldReturnHitsAboveThreshold() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow highSimRow = new ChunkRow();
        highSimRow.setChunkId(1L);
        highSimRow.setDocumentId(10L);
        highSimRow.setContent("relevant content");
        highSimRow.setSimilarity(0.85);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(highSimRow));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.isNoHits()).isFalse();
        assertThat(result.getChunks()).hasSize(1);
        assertThat(result.getChunks().get(0).getChunkId()).isEqualTo(1L);
        assertThat(result.getChunks().get(0).getContent()).isEqualTo("relevant content");
        assertThat(result.getHitChunkIds()).containsExactly(1L);
    }

    @Test
    void shouldTruncateSingleChunkContent() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        String longContent = "A".repeat(500);
        ChunkRow row = new ChunkRow();
        row.setChunkId(1L);
        row.setDocumentId(10L);
        row.setContent(longContent);
        row.setSimilarity(0.85);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(row));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 100);

        assertThat(result.getChunks().get(0).getContent()).hasSize(100);
    }

    @Test
    void shouldDeduplicateByChunkId() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow row1 = new ChunkRow();
        row1.setChunkId(1L);
        row1.setDocumentId(10L);
        row1.setContent("content A");
        row1.setSimilarity(0.9);
        ChunkRow row2 = new ChunkRow();
        row2.setChunkId(1L);
        row2.setDocumentId(10L);
        row2.setContent("content A duplicate");
        row2.setSimilarity(0.8);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(row1, row2));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.getChunks()).hasSize(1);
    }

    @Test
    void shouldRespectMaxContextChunks() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow r1 = createRow(1L, "c1", 0.9);
        ChunkRow r2 = createRow(2L, "c2", 0.8);
        ChunkRow r3 = createRow(3L, "c3", 0.7);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(r1, r2, r3));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.5, 2, 12000, 3000);

        assertThat(result.getChunks()).hasSize(2);
    }

    @Test
    void shouldRespectTopKWhenMaxContextChunksIsHigher() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow r1 = createRow(1L, "c1", 0.9);
        ChunkRow r2 = createRow(2L, "c2", 0.8);
        ChunkRow r3 = createRow(3L, "c3", 0.7);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), eq(2)))
                .thenReturn(List.of(r1, r2, r3));

        RetrievalResult result = retrievalService.retrieve("query", kb, 2, 0.5, 5, 12000, 3000);

        assertThat(result.getChunks()).hasSize(2);
        verify(retrievalMapper).retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), eq(2));
    }

    @Test
    void shouldRespectTotalContextCharsForFirstChunk() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow row = createRow(1L, "A".repeat(500), 0.9);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(row));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.5, 5, 120, 3000);

        assertThat(result.getChunks()).hasSize(1);
        assertThat(result.getChunks().get(0).getContent()).hasSize(120);
    }

    private ChunkRow createRow(Long chunkId, String content, double similarity) {
        ChunkRow row = new ChunkRow();
        row.setChunkId(chunkId);
        row.setDocumentId(10L);
        row.setContent(content);
        row.setSimilarity(similarity);
        return row;
    }

    @Test
    void shouldPassQueryToEmbeddingClient() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("test query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        retrievalService.retrieve("test query", kb, 5, 0.7, 5, 12000, 3000);

        verify(embeddingClient).embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("test query")), eq(1536));
    }

    @Test
    void testVectorToPgString() {
        float[] vector = {0.1f, 0.2f, -0.3f};
        String result = RetrievalService.vectorToPgString(vector);

        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
        assertThat(result).contains("0.10000000");
        assertThat(result).contains("-0.300000");
    }

    @Test
    void shouldAssignCitationIdsInFinalInjectionOrderMatchingHitChunkIds() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow r1 = createRow(1L, "c1", 0.9);
        r1.setKnowledgeBaseId(KB_ID);
        r1.setChunkIndex(0);
        r1.setSourceFilename("handbook.md");
        ChunkRow r2 = createRow(2L, "c2", 0.8);
        r2.setKnowledgeBaseId(KB_ID);
        r2.setChunkIndex(1);
        r2.setSourceFilename("guide.md");
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(r1, r2));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.5, 5, 12000, 3000);

        assertThat(result.getCitations()).hasSize(2);
        assertThat(result.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(result.getCitations().get(1).getCitationId()).isEqualTo("S2");
        assertThat(result.getCitations().get(0).getChunkId()).isEqualTo(1L);
        assertThat(result.getCitations().get(1).getChunkId()).isEqualTo(2L);
        assertThat(result.getHitChunkIds()).containsExactly(1L, 2L);
        assertThat(result.getCitations().get(0).getSourceFilename()).isEqualTo("handbook.md");
        assertThat(result.getCitations().get(0).getKnowledgeBaseId()).isEqualTo(KB_ID);
        assertThat(result.getCitations().get(0).getChunkIndex()).isZero();
    }

    @Test
    void shouldNotAssignCitationsForExcludedLowSimilarityChunk() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow r1 = createRow(1L, "c1", 0.9);
        ChunkRow low = createRow(2L, "c2", 0.5);
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(r1, low));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(result.getCitations().get(0).getChunkId()).isEqualTo(1L);
        assertThat(result.getHitChunkIds()).containsExactly(1L);
    }

    @Test
    void shouldBuildNoHitEvidenceWithEmptyCitations() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.7, 5, 12000, 3000);

        assertThat(result.getEvidence()).isNotNull();
        assertThat(result.getEvidence().isNoHits()).isTrue();
        assertThat(result.getEvidence().getCitations()).isEmpty();
        assertThat(result.getEvidence().getVersion()).isEqualTo(1);
        assertThat(result.getEvidence().getTopK()).isEqualTo(5);
    }

    @Test
    void shouldFilterMetadataToSafeKeysOnly() {
        KnowledgeBaseEntity kb = createReadyKb();
        ModelConfigEntity config = createEmbeddingConfig();
        when(modelConfigService.findEnabledEmbeddingConfig(USER_ID, "text-embedding-3-small", 1536))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("sk-test");
        when(embeddingClient.embed(anyString(), eq("sk-test"), eq("text-embedding-3-small"),
                eq(List.of("query")), eq(1536)))
                .thenReturn(List.of(new float[1536]));

        ChunkRow row = createRow(1L, "c1", 0.9);
        row.setMetadata("{\"source\":\"handbook.md\",\"parser\":\"markdown\",\"secret\":\"leak\"}");
        when(retrievalMapper.retrieveChunks(anyString(), eq(USER_ID), eq(KB_ID), anyInt()))
                .thenReturn(List.of(row));

        RetrievalResult result = retrievalService.retrieve("query", kb, 5, 0.5, 5, 12000, 3000);

        assertThat(result.getCitations().get(0).getMetadata()).containsOnlyKeys("source", "parser");
    }
}
