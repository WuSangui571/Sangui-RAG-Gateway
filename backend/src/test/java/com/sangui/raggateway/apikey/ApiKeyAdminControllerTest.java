package com.sangui.raggateway.apikey;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAdminControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private AppService appService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiKeyAdminController controller = new ApiKeyAdminController(apiKeyService, appService);
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

    // ---- Enable ----

    @Test
    void shouldEnableDisabledKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "DISABLED");
        ApiKeyEntity enabled = createKey(10L, 1L, 100L, "ACTIVE");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.enable(10L, 100L)).thenReturn(enabled);

        mockMvc.perform(post("/api/admin/api-keys/10/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.key").doesNotExist())
                .andExpect(jsonPath("$.data.key_hash").doesNotExist());
    }

    @Test
    void shouldReturn404ForEnableOfNonExistentKey() throws Exception {
        when(apiKeyService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/api-keys/999/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForEnableOfCrossUserKey() throws Exception {
        ApiKeyEntity otherUserKey = createKey(10L, 1L, 200L, "DISABLED");
        when(apiKeyService.findById(10L)).thenReturn(otherUserKey);

        mockMvc.perform(post("/api/admin/api-keys/10/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRejectEnableOfRevokedKey() throws Exception {
        ApiKeyEntity revokedKey = createKey(10L, 1L, 100L, "REVOKED");
        when(apiKeyService.findById(10L)).thenReturn(revokedKey);
        when(apiKeyService.enable(10L, 100L))
                .thenThrow(new IllegalArgumentException("Revoked key cannot be enabled"));

        mockMvc.perform(post("/api/admin/api-keys/10/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectEnableOfExpiredKey() throws Exception {
        ApiKeyEntity expiredKey = createKey(10L, 1L, 100L, "EXPIRED");
        when(apiKeyService.findById(10L)).thenReturn(expiredKey);
        when(apiKeyService.enable(10L, 100L))
                .thenThrow(new IllegalArgumentException("Expired key cannot be enabled"));

        mockMvc.perform(post("/api/admin/api-keys/10/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- Detect ----

    @Test
    void shouldDetectActiveKeyWithEnabledApp() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.key_id").value(10))
                .andExpect(jsonPath("$.data.app_id").value(1))
                .andExpect(jsonPath("$.data.usable").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.app_enabled").value(true))
                .andExpect(jsonPath("$.data.expires_at").doesNotExist())
                .andExpect(jsonPath("$.data.checked_at").exists())
                .andExpect(jsonPath("$.data.key").doesNotExist())
                .andExpect(jsonPath("$.data.key_hash").doesNotExist());
    }

    @Test
    void shouldDetectDisabledKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "DISABLED");
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(false);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.app_enabled").value(true));
    }

    @Test
    void shouldDetectRevokedKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "REVOKED");
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(false);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void shouldDetectExpiredKey() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        key.setExpiresAt(LocalDateTime.now().minusDays(1));
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(false);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldDetectKeyWithDisabledApp() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        AppEntity app = createApp(1L, "DISABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(false);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.app_enabled").value(false));
    }

    @Test
    void shouldDetectKeyWithMissingAppAsUnusable() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.app_enabled").value(false));
    }

    @Test
    void shouldDetectKeyWithCrossUserAppAsUnusable() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        AppEntity app = createApp(1L, 200L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(app);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.app_enabled").value(false));

        verify(appService, never()).isEnabled(app);
    }

    @Test
    void shouldReturn404ForDetectOfNonExistentKey() throws Exception {
        when(apiKeyService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/api-keys/999/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForDetectOfCrossUserKey() throws Exception {
        ApiKeyEntity otherUserKey = createKey(10L, 1L, 200L, "ACTIVE");
        when(apiKeyService.findById(10L)).thenReturn(otherUserKey);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRejectMissingAdminHeaderForDetect() throws Exception {
        mockMvc.perform(post("/api/admin/api-keys/10/detect"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectNonPositiveAdminHeaderForDetect() throws Exception {
        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldNotContainKeyOrHashInDetectResponse() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"key\""))))
                .andExpect(content().string(not(containsString("\"key_hash\""))))
                .andExpect(content().string(not(containsString("\"authorization\""))));
    }

    @Test
    void shouldNotCallUpdateLastUsedForDetect() throws Exception {
        ApiKeyEntity key = createKey(10L, 1L, 100L, "ACTIVE");
        AppEntity app = createApp(1L, "ENABLED");

        when(apiKeyService.findById(10L)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(1L)).thenReturn(app);
        when(appService.isEnabled(app)).thenReturn(true);

        mockMvc.perform(post("/api/admin/api-keys/10/detect")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk());

        verify(apiKeyService, never()).updateLastUsed(anyLong());
    }

    private AppEntity createApp(Long id, String status) {
        return createApp(id, 100L, status);
    }

    private AppEntity createApp(Long id, Long userId, String status) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setStatus(status);
        return app;
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
