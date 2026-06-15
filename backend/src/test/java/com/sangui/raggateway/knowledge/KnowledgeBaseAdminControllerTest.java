package com.sangui.raggateway.knowledge;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAdminControllerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KnowledgeBaseAdminController controller = new KnowledgeBaseAdminController(knowledgeBaseService);
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

    @Test
    void shouldCreateKnowledgeBase() throws Exception {
        KnowledgeBaseEntity entity = createKb(1L, 100L, "Product Docs");
        when(knowledgeBaseService.create(eq(100L), eq("Product Docs"), eq("text-embedding-3-small"), eq(1536)))
                .thenReturn(entity);

        mockMvc.perform(post("/api/admin/knowledge-bases")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Product Docs",
                                    "embedding_model": "text-embedding-3-small",
                                    "embedding_dimension": 1536
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.name").value("Product Docs"))
                .andExpect(jsonPath("$.data.embedding_model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.data.embedding_dimension").value(1536))
                .andExpect(jsonPath("$.data.status").value("EMPTY"));
    }

    @Test
    void shouldRejectCreateKbWithBlankName() throws Exception {
        when(knowledgeBaseService.create(eq(100L), eq(""), eq("text-embedding-3-small"), eq(1536)))
                .thenThrow(new IllegalArgumentException("name is required"));

        mockMvc.perform(post("/api/admin/knowledge-bases")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "embedding_model": "text-embedding-3-small",
                                    "embedding_dimension": 1536
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectCreateKbWithBlankEmbeddingModel() throws Exception {
        when(knowledgeBaseService.create(eq(100L), eq("Product Docs"), eq(""), eq(1536)))
                .thenThrow(new IllegalArgumentException("embeddingModel is required"));

        mockMvc.perform(post("/api/admin/knowledge-bases")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Product Docs",
                                    "embedding_model": "",
                                    "embedding_dimension": 1536
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectCreateKbWithNonPositiveDimension() throws Exception {
        when(knowledgeBaseService.create(eq(100L), eq("Product Docs"), eq("text-embedding-3-small"), eq(0)))
                .thenThrow(new IllegalArgumentException("embeddingDimension must be positive"));

        mockMvc.perform(post("/api/admin/knowledge-bases")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Product Docs",
                                    "embedding_model": "text-embedding-3-small",
                                    "embedding_dimension": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldListKnowledgeBases() throws Exception {
        KnowledgeBaseEntity entity = createKb(1L, 100L, "Product Docs");
        when(knowledgeBaseService.listByUserId(eq(100L), isNull())).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/admin/knowledge-bases")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].user_id").value(100));
    }

    @Test
    void shouldListKnowledgeBasesWithStatusFilter() throws Exception {
        KnowledgeBaseEntity entity = createKb(1L, 100L, "Product Docs");
        when(knowledgeBaseService.listByUserId(eq(100L), eq("EMPTY"))).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/admin/knowledge-bases?status=EMPTY")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("EMPTY"));
    }

    @Test
    void shouldRejectInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/knowledge-bases?status=INVALID")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldGetKnowledgeBaseDetail() throws Exception {
        KnowledgeBaseEntity entity = createKb(1L, 100L, "Product Docs");
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(entity);

        mockMvc.perform(get("/api/admin/knowledge-bases/1")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100));
    }

    @Test
    void shouldReturn404ForMissingKnowledgeBase() throws Exception {
        when(knowledgeBaseService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(knowledgeBaseService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/999")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForCrossUserKnowledgeBase() throws Exception {
        KnowledgeBaseEntity otherKb = createKb(1L, 200L, "Other Docs");
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(knowledgeBaseService.findById(1L)).thenReturn(otherKb);

        mockMvc.perform(get("/api/admin/knowledge-bases/1")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectMissingAdminUserIdHeader() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Product Docs",
                                    "embedding_model": "text-embedding-3-small",
                                    "embedding_dimension": 1536
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectNonPositiveAdminUserId() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Product Docs",
                                    "embedding_model": "text-embedding-3-small",
                                    "embedding_dimension": 1536
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(knowledgeBaseService);
    }

    private KnowledgeBaseEntity createKb(Long id, Long userId, String name) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setEmbeddingModel("text-embedding-3-small");
        entity.setEmbeddingDimension(1536);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
