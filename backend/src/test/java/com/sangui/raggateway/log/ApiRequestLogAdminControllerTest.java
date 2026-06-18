package com.sangui.raggateway.log;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.log.vo.ApiRequestLogDetailVO;
import com.sangui.raggateway.log.vo.ApiRequestLogPageVO;
import com.sangui.raggateway.log.vo.ApiRequestLogVO;
import com.sangui.raggateway.log.vo.HitChunkSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiRequestLogAdminControllerTest {

    @Mock
    private AppService appService;

    @Mock
    private ApiRequestLogService apiRequestLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiRequestLogAdminController controller = new ApiRequestLogAdminController(
                appService, apiRequestLogService, new OutputCaptureProperties());
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setUpAuthContext() {
        AdminAuthContextHolder.set(new AdminAuthContext(100L, "testuser"));
    }

    @AfterEach
    void tearDownAuthContext() {
        AdminAuthContextHolder.clear();
    }

    // ---- Admin identity ----

    @Test
    void shouldRejectMissingAdminUserIdHeader() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(get("/api/admin/apps/1/request-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectNonPositiveAdminUserId() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(get("/api/admin/apps/1/request-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---- App ownership ----

    @Test
    void shouldReturn403ForCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(apiRequestLogService);
    }

    @Test
    void shouldReturn404ForMissingApp() throws Exception {
        when(appService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/apps/999/request-logs")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(apiRequestLogService);
    }

    // ---- List request logs ----

    @Test
    void shouldListRequestLogsWithDefaultPagination() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogVO logVO = createLogVO(1L, "req-001", "success");
        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = ApiRequestLogPageVO.of(List.of(logVO), 1, 20, 1L);
        when(apiRequestLogService.listRequestLogs(eq(100L), eq(1L), any(ApiRequestLogQuery.class))).thenReturn(pageVO);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].request_id").value("req-001"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldListRequestLogsWithFilters() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogVO logVO = createLogVO(1L, "req-001", "failure");
        logVO.setErrorCode("upstream_error");
        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = ApiRequestLogPageVO.of(List.of(logVO), 2, 10, 1L);
        when(apiRequestLogService.listRequestLogs(eq(100L), eq(1L), any(ApiRequestLogQuery.class))).thenReturn(pageVO);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("page", "2")
                        .param("page_size", "10")
                        .param("status", "failure")
                        .param("error_code", "upstream_error")
                        .param("start_time", "2026-05-01T00:00:00")
                        .param("end_time", "2026-06-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].status").value("failure"))
                .andExpect(jsonPath("$.data.items[0].error_code").value("upstream_error"));
    }

    @Test
    void shouldReturnEmptyListWhenNoLogs() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = ApiRequestLogPageVO.of(Collections.emptyList(), 1, 20, 0L);
        when(apiRequestLogService.listRequestLogs(eq(100L), eq(1L), any(ApiRequestLogQuery.class))).thenReturn(pageVO);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ---- List invalid params ----

    @Test
    void shouldRejectInvalidPage() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectInvalidPageSize() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("page_size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectInvalidStatus() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("status", "invalid_status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldAcceptKnownRequestLogStatuses() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = ApiRequestLogPageVO.of(Collections.emptyList(), 1, 20, 0L);
        when(apiRequestLogService.listRequestLogs(eq(100L), eq(1L), any(ApiRequestLogQuery.class))).thenReturn(pageVO);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("status", "SUCCESS"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("status", "failure"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("status", "cancelled"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStartTimeAfterEndTime() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("start_time", "2026-06-01T00:00:00")
                        .param("end_time", "2026-05-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectInvalidTimeFormat() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")

                        .param("start_time", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- Sensitive field non-disclosure ----

    @Test
    void shouldNotContainSensitiveFieldsInListResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogVO logVO = createLogVO(1L, "req-001", "success");
        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = ApiRequestLogPageVO.of(List.of(logVO), 1, 20, 1L);
        when(apiRequestLogService.listRequestLogs(eq(100L), eq(1L), any(ApiRequestLogQuery.class))).thenReturn(pageVO);

        mockMvc.perform(get("/api/admin/apps/1/request-logs")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].prompt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].messages").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].augmented_prompt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].api_key").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].key_hash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].upstream_api_key").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].chunk_content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].embedding").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].provider_response_body").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].stack_trace").doesNotExist());
    }

    // ---- Request log detail ----

    @Test
    void shouldGetRequestLogDetail() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogDetailVO detail = createDetailVO(1L, "req-001");
        when(apiRequestLogService.getRequestLogDetail(100L, 1L, "req-001")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.request_id").value("req-001"))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.updated_at").exists());
    }

    @Test
    void shouldReturnRetrievalEvidenceInDetailWhenPresent() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogDetailVO detail = createDetailVO(1L, "req-001");
        com.sangui.raggateway.log.vo.RetrievalEvidenceVO evidence = new com.sangui.raggateway.log.vo.RetrievalEvidenceVO();
        evidence.setVersion(1);
        evidence.setNoHits(false);
        evidence.setRetrievalLatencyMs(42L);
        evidence.setTopK(5);
        evidence.setSimilarityThreshold(0.3);
        evidence.setMaxContextChunks(5);
        com.sangui.raggateway.log.vo.CitationVO citation = new com.sangui.raggateway.log.vo.CitationVO();
        citation.setCitationId("S1");
        citation.setChunkId(8L);
        citation.setDocumentId(4L);
        citation.setKnowledgeBaseId(2L);
        citation.setSourceFilename("handbook.md");
        citation.setChunkIndex(0);
        citation.setSimilarity(0.842);
        evidence.setCitations(List.of(citation));
        detail.setRetrievalEvidence(evidence);

        when(apiRequestLogService.getRequestLogDetail(100L, 1L, "req-001")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrieval_evidence.version").value(1))
                .andExpect(jsonPath("$.data.retrieval_evidence.no_hits").value(false))
                .andExpect(jsonPath("$.data.retrieval_evidence.citations[0].citation_id").value("S1"))
                .andExpect(jsonPath("$.data.retrieval_evidence.citations[0].source_filename").value("handbook.md"))
                .andExpect(jsonPath("$.data.retrieval_evidence.citations[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.retrieval_evidence.citations[0].chunk_content").doesNotExist())
                .andExpect(jsonPath("$.data.retrieval_evidence.citations[0].embedding").doesNotExist());
    }

    @Test
    void shouldReturn404ForMissingRequestLog() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);
        when(apiRequestLogService.getRequestLogDetail(100L, 1L, "nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/nonexistent")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldNotContainSensitiveFieldsInDetailResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogDetailVO detail = createDetailVO(1L, "req-001");
        when(apiRequestLogService.getRequestLogDetail(100L, 1L, "req-001")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prompt").doesNotExist())
                .andExpect(jsonPath("$.data.messages").doesNotExist())
                .andExpect(jsonPath("$.data.full_messages").doesNotExist())
                .andExpect(jsonPath("$.data.augmented_prompt").doesNotExist())
                .andExpect(jsonPath("$.data.api_key").doesNotExist())
                .andExpect(jsonPath("$.data.key_hash").doesNotExist())
                .andExpect(jsonPath("$.data.authorization").doesNotExist())
                .andExpect(jsonPath("$.data.upstream_api_key").doesNotExist())
                .andExpect(jsonPath("$.data.api_key_encrypted").doesNotExist())
                .andExpect(jsonPath("$.data.chunk_content").doesNotExist())
                .andExpect(jsonPath("$.data.embedding").doesNotExist())
                .andExpect(jsonPath("$.data.provider_response_body").doesNotExist())
                .andExpect(jsonPath("$.data.stack_trace").doesNotExist());
    }

    // ---- Hit chunks ----

    @Test
    void shouldGetHitChunkSummaries() throws Exception {
        AppEntity app = createApp(1L, 100L);
        app.setDefaultKnowledgeBaseId(6L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        HitChunkSummaryVO summary = new HitChunkSummaryVO();
        List<HitChunkSummaryVO> summaries = List.of(summary);
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-001"))
                .thenReturn(new ApiRequestLogEntity());
        when(apiRequestLogService.getHitChunkSummaries(100L, 1L, 6L, "req-001")).thenReturn(summaries);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001/hit-chunks")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldReturnEmptyListForNullHitChunkIds() throws Exception {
        AppEntity app = createApp(1L, 100L);
        app.setDefaultKnowledgeBaseId(6L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        when(apiRequestLogService.getHitChunkSummaries(100L, 1L, 6L, "req-002"))
                .thenReturn(Collections.emptyList());
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-002"))
                .thenReturn(new ApiRequestLogEntity());

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-002/hit-chunks")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldReturn404ForMissingRequestLogHitChunks() throws Exception {
        AppEntity app = createApp(1L, 100L);
        app.setDefaultKnowledgeBaseId(6L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "missing"))
                .thenReturn(null);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/missing/hit-chunks")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(apiRequestLogService, never()).getHitChunkSummaries(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldRejectHitChunksWithoutDefaultKb() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001/hit-chunks")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(apiRequestLogService, never()).getHitChunkSummaries(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldRejectHitChunksForCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(get("/api/admin/apps/1/request-logs/req-001/hit-chunks")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(apiRequestLogService);
    }

    // ---- Output preview explicit access ----

    @Test
    void shouldAccessOutputPreviewWithExplicitConfirmation() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setId(9L);
        entity.setRequestId("req-001");
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-001"))
                .thenReturn(entity);

        com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO preview =
                new com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO();
        preview.setRequestId("req-001");
        preview.setOutputCaptureStatus("CAPTURED");
        preview.setCompletionLength(12);
        preview.setOutputPreview("safe preview");
        preview.setOutputPreviewTruncated(false);
        preview.setOutputRedacted(false);
        when(apiRequestLogService.getOutputPreview(100L, 1L, "req-001")).thenReturn(preview);

        mockMvc.perform(post("/api/admin/apps/1/request-logs/req-001/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": true,
                                  "reason": "Investigating truncation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.request_id").value("req-001"))
                .andExpect(jsonPath("$.data.output_capture_status").value("CAPTURED"))
                .andExpect(jsonPath("$.data.output_preview").value("safe preview"));

        verify(apiRequestLogService).writeAccessAudit(100L, 1L, 9L,
                "req-001", "GRANTED", "Investigating truncation");
    }

    @Test
    void shouldTrimOutputPreviewAccessReasonBeforeAudit() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setId(9L);
        entity.setRequestId("req-001");
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-001"))
                .thenReturn(entity);

        com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO preview =
                new com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO();
        preview.setRequestId("req-001");
        preview.setOutputCaptureStatus("CAPTURED");
        preview.setOutputPreview("safe preview");
        when(apiRequestLogService.getOutputPreview(100L, 1L, "req-001")).thenReturn(preview);

        mockMvc.perform(post("/api/admin/apps/1/request-logs/req-001/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": true,
                                  "reason": "  Investigating truncation  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(apiRequestLogService).writeAccessAudit(100L, 1L, 9L,
                "req-001", "GRANTED", "Investigating truncation");
    }

    @Test
    void shouldRejectOutputPreviewAccessWithoutConfirmationAndAuditDenied() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setId(9L);
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-001"))
                .thenReturn(entity);

        mockMvc.perform(post("/api/admin/apps/1/request-logs/req-001/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(apiRequestLogService).writeAccessAudit(100L, 1L, 9L,
                "req-001", "DENIED", null);
        verify(apiRequestLogService, never()).getOutputPreview(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldRejectOutputPreviewAccessWithTooLongReasonAndAuditDenied() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setId(9L);
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "req-001"))
                .thenReturn(entity);

        String longReason = "x".repeat(257);
        mockMvc.perform(post("/api/admin/apps/1/request-logs/req-001/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": true,
                                  "reason": "%s"
                                }
                                """.formatted(longReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(apiRequestLogService).writeAccessAudit(100L, 1L, 9L,
                "req-001", "DENIED", longReason);
        verify(apiRequestLogService, never()).getOutputPreview(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldReturn404ForMissingOutputPreviewLogAndAuditNotFound() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);
        when(apiRequestLogService.findByRequestIdAndUserAndApp(100L, 1L, "missing"))
                .thenReturn(null);

        mockMvc.perform(post("/api/admin/apps/1/request-logs/missing/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(apiRequestLogService).writeAccessAudit(100L, 1L, null,
                "missing", "NOT_FOUND", null);
        verify(apiRequestLogService, never()).getOutputPreview(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldRejectOutputPreviewForCrossUserAppBeforeLogQuery() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(post("/api/admin/apps/1/request-logs/req-001/output-preview/access")

                        .contentType("application/json")
                        .content("""
                                {
                                  "confirm_access": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(apiRequestLogService);
    }

    private AppEntity createApp(Long id, Long userId) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setName("Demo App");
        app.setStatus("ENABLED");
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return app;
    }

    private ApiRequestLogVO createLogVO(Long id, String requestId, String status) {
        ApiRequestLogVO vo = new ApiRequestLogVO();
        vo.setId(id);
        vo.setRequestId(requestId);
        vo.setAppId(1L);
        vo.setApiKeyId(30L);
        vo.setModel("deepseek-v4-pro");
        vo.setProviderName("sanguicode");
        vo.setStatus(status);
        vo.setLatencyMs(1234L);
        vo.setUpstreamLatencyMs(1100L);
        vo.setMessagesCount(2);
        vo.setQuestionSummary("test question");
        vo.setHitChunkIds(List.of(9L, 8L));
        vo.setCreatedAt(LocalDateTime.of(2026, 5, 31, 12, 0));
        return vo;
    }

    private ApiRequestLogDetailVO createDetailVO(Long id, String requestId) {
        ApiRequestLogDetailVO vo = new ApiRequestLogDetailVO();
        vo.setId(id);
        vo.setRequestId(requestId);
        vo.setAppId(1L);
        vo.setApiKeyId(30L);
        vo.setUserId(100L);
        vo.setModel("deepseek-v4-pro");
        vo.setProviderName("sanguicode");
        vo.setStatus("success");
        vo.setLatencyMs(1234L);
        vo.setUpstreamLatencyMs(1100L);
        vo.setMessagesCount(2);
        vo.setQuestionSummary("test question");
        vo.setHitChunkIds(List.of(9L, 8L));
        vo.setCreatedAt(LocalDateTime.of(2026, 5, 31, 12, 0));
        vo.setUpdatedAt(LocalDateTime.of(2026, 5, 31, 12, 0));
        return vo;
    }
}
