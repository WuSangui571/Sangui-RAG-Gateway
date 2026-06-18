package com.sangui.raggateway.retrieval.evaluation;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RetrievalEvaluationAdminControllerTest {

    @Mock
    private AppService appService;

    @Mock
    private RetrievalEvaluationService evaluationService;

    private MockMvc mockMvc;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        RetrievalEvaluationAdminController controller =
                new RetrievalEvaluationAdminController(appService, evaluationService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setUpAuthContext() {
        AdminAuthContextHolder.set(new AdminAuthContext(USER_ID, "testuser"));
    }

    @AfterEach
    void tearDownAuthContext() {
        AdminAuthContextHolder.clear();
    }

    private AppEntity createOwnedApp() {
        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setStatus("ENABLED");
        return app;
    }

    @Test
    void shouldRejectMissingAuthContext() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectCrossUserAppBeforeEvaluation() throws Exception {
        AppEntity otherUserApp = createOwnedApp();
        otherUserApp.setUserId(200L);
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(null);
        when(appService.findById(APP_ID)).thenReturn(otherUserApp);

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(evaluationService);
    }

    @Test
    void shouldRejectMissingApp() throws Exception {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(null);
        when(appService.findById(APP_ID)).thenReturn(null);

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(evaluationService);
    }

    @Test
    void shouldRejectInvalidLimit() throws Exception {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createOwnedApp());
        org.mockito.Mockito.doThrow(new BusinessException("INVALID_REQUEST", "limit must be between 1 and 100"))
                .when(evaluationService).validateLimit(eq(0));

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{\"limit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturnRunResultForOwnedApp() throws Exception {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createOwnedApp());

        RetrievalEvaluationCaseResult caseResult = new RetrievalEvaluationCaseResult(
                "case-001", "query", List.of(8L), List.of(8L), List.of(4L), List.of(4L),
                true, 1, 1.0, 1.0, 1.0, false, null);
        RetrievalEvaluationRunResult runResult = new RetrievalEvaluationRunResult(
                APP_ID, 20L, 1, 1, 1.0, 1.0, 1.0, List.of(caseResult));
        when(evaluationService.run(eq(APP_ID), eq(USER_ID), any(), any())).thenReturn(runResult);

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{\"case_ids\":[\"case-001\"],\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.app_id").value(1))
                .andExpect(jsonPath("$.data.knowledge_base_id").value(20))
                .andExpect(jsonPath("$.data.case_count").value(1))
                .andExpect(jsonPath("$.data.hit_count").value(1))
                .andExpect(jsonPath("$.data.precision_at_k").value(1.0))
                .andExpect(jsonPath("$.data.cases[0].case_id").value("case-001"))
                .andExpect(jsonPath("$.data.cases[0].hit").value(true))
                .andExpect(jsonPath("$.data.cases[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.cases[0].chunk_content").doesNotExist())
                .andExpect(jsonPath("$.data.cases[0].embedding").doesNotExist())
                .andExpect(jsonPath("$.data.cases[0].storage_path").doesNotExist());
    }

    @Test
    void shouldPropagateKnowledgeBaseNotReadyAsBadRequest() throws Exception {
        when(appService.findByIdAndUserId(APP_ID, USER_ID)).thenReturn(createOwnedApp());
        when(evaluationService.run(eq(APP_ID), eq(USER_ID), any(), any()))
                .thenThrow(new BusinessException("KNOWLEDGE_BASE_NOT_READY",
                        "Knowledge base is not ready", org.springframework.http.HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/admin/apps/1/retrieval-evaluations/runs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_NOT_READY"));
    }
}
