package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAdminControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiKeyAdminController controller = new ApiKeyAdminController(apiKeyService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---- Disable ----

    @Test
    void shouldDisableActiveKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        ApiKeyEntity disabled = createKey(10L, 1L, 100L, "DISABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.disable(10L, 100L)).thenReturn(disabled);

        mockMvc.perform(post("/api/admin/api-keys/10/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.key").doesNotExist())
                .andExpect(jsonPath("$.data.key_hash").doesNotExist());
    }

    @Test
    void shouldReturn404ForDisableOfNonExistentKey() throws Exception {
        when(apiKeyService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/api-keys/999/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForDisableOfCrossUserKey() throws Exception {
        ApiKeyEntity otherUserKey = createKey(10L, 1L, 200L, "ACTIVE");
        when(apiKeyService.findById(10L)).thenReturn(otherUserKey);

        mockMvc.perform(post("/api/admin/api-keys/10/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRejectDisableOfRevokedKey() throws Exception {
        ApiKeyEntity revokedKey = createKey(10L, 1L, 100L, "REVOKED");
        when(apiKeyService.findById(10L)).thenReturn(revokedKey);
        when(apiKeyService.disable(10L, 100L))
                .thenThrow(new IllegalArgumentException("Revoked key cannot be disabled"));

        mockMvc.perform(post("/api/admin/api-keys/10/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- Revoke ----

    @Test
    void shouldRevokeActiveKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        ApiKeyEntity revoked = createKey(10L, 1L, 100L, "REVOKED");
        revoked.setRevokedAt(LocalDateTime.now());

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.revoke(10L, 100L)).thenReturn(revoked);

        mockMvc.perform(post("/api/admin/api-keys/10/revoke")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revoked_at").exists())
                .andExpect(jsonPath("$.data.key").doesNotExist())
                .andExpect(jsonPath("$.data.key_hash").doesNotExist());
    }

    @Test
    void shouldReturn404ForRevokeOfNonExistentKey() throws Exception {
        when(apiKeyService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/api-keys/999/revoke")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForRevokeOfCrossUserKey() throws Exception {
        ApiKeyEntity otherUserKey = createKey(10L, 1L, 200L, "ACTIVE");
        when(apiKeyService.findById(10L)).thenReturn(otherUserKey);

        mockMvc.perform(post("/api/admin/api-keys/10/revoke")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---- Admin identity ----

    @Test
    void shouldRejectMissingAdminHeaderForDisable() throws Exception {
        mockMvc.perform(post("/api/admin/api-keys/10/disable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectNonPositiveAdminHeaderForRevoke() throws Exception {
        mockMvc.perform(post("/api/admin/api-keys/10/revoke")
                        .header("X-Admin-User-Id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(apiKeyService);
    }

    // ---- Secret safety ----

    @Test
    void shouldNotContainKeyOrHashInDisableResponse() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        ApiKeyEntity disabled = createKey(10L, 1L, 100L, "DISABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.disable(10L, 100L)).thenReturn(disabled);

        mockMvc.perform(post("/api/admin/api-keys/10/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"key\""))))
                .andExpect(content().string(not(containsString("\"key_hash\""))));
    }

    private ApiKeyEntity createKey(Long id, Long appId, Long userId, String status) {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(id);
        key.setAppId(appId);
        key.setUserId(userId);
        key.setName("Test Key");
        key.setKeyPrefix("sk-sang-abc12345");
        key.setStatus(status);
        key.setCreatedAt(LocalDateTime.now());
        key.setUpdatedAt(LocalDateTime.now());
        return key;
    }
}
