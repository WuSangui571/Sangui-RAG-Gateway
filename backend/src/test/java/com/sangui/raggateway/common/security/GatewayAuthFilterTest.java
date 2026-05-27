package com.sangui.raggateway.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayAuthFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private AppService appService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyHasher apiKeyHasher;
    private ApiKeyGenerator apiKeyGenerator;
    private ObjectMapper objectMapper;
    private GatewayAuthFilter filter;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long API_KEY_ID = 10L;

    @BeforeEach
    void setUp() {
        apiKeyHasher = new ApiKeyHasher();
        apiKeyGenerator = new ApiKeyGenerator();
        objectMapper = new ObjectMapper();
        filter = new GatewayAuthFilter(apiKeyHasher, apiKeyService, appService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        GatewayRequestContextHolder.clear();
    }

    private String setupValidKey() {
        String plaintext = apiKeyGenerator.generate();
        String hash = apiKeyHasher.hash(plaintext);

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setStatus("ENABLED");

        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.setId(API_KEY_ID);
        apiKey.setAppId(APP_ID);
        apiKey.setUserId(USER_ID);
        apiKey.setKeyHash(hash);
        apiKey.setKeyPrefix(apiKeyGenerator.extractPrefix(plaintext));
        apiKey.setStatus("ACTIVE");

        lenient().when(apiKeyService.findByHash(hash)).thenReturn(apiKey);
        lenient().when(apiKeyService.isValid(apiKey)).thenReturn(true);
        lenient().when(appService.findById(APP_ID)).thenReturn(app);
        lenient().when(appService.isEnabled(app)).thenReturn(true);

        return plaintext;
    }

    @Test
    void shouldAuthenticateValidKey() throws Exception {
        String validPlaintext = setupValidKey();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + validPlaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(apiKeyService).updateLastUsed(API_KEY_ID);
    }

    @Test
    void shouldClearContextAfterRequest() throws Exception {
        String validPlaintext = setupValidKey();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + validPlaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
        });

        assertThat(GatewayRequestContextHolder.get()).isNull();
    }

    @Test
    void shouldReturn401ForMissingAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
        assertThat(response.getContentAsString()).contains("Invalid API key.");
    }

    @Test
    void shouldReturn401ForNonBearerScheme() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForEmptyBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForMalformedKeyPrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer sk-wrong-prefix-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForUnknownKeyHash() throws Exception {
        String unknownKey = apiKeyGenerator.generate();
        when(apiKeyService.findByHash(apiKeyHasher.hash(unknownKey))).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + unknownKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForDisabledKey() throws Exception {
        String disabledKey = apiKeyGenerator.generate();
        String disabledHash = apiKeyHasher.hash(disabledKey);

        ApiKeyEntity disabled = new ApiKeyEntity();
        disabled.setId(20L);
        disabled.setAppId(APP_ID);
        disabled.setStatus("DISABLED");

        when(apiKeyService.findByHash(disabledHash)).thenReturn(disabled);
        when(apiKeyService.isValid(disabled)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + disabledKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForRevokedKey() throws Exception {
        String revokedKey = apiKeyGenerator.generate();
        String revokedHash = apiKeyHasher.hash(revokedKey);

        ApiKeyEntity revoked = new ApiKeyEntity();
        revoked.setId(30L);
        revoked.setAppId(APP_ID);
        revoked.setStatus("REVOKED");

        when(apiKeyService.findByHash(revokedHash)).thenReturn(revoked);
        when(apiKeyService.isValid(revoked)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + revokedKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForExpiredKey() throws Exception {
        String expiredKey = apiKeyGenerator.generate();
        String expiredHash = apiKeyHasher.hash(expiredKey);

        ApiKeyEntity expired = new ApiKeyEntity();
        expired.setId(40L);
        expired.setAppId(APP_ID);
        expired.setStatus("ACTIVE");
        expired.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));

        when(apiKeyService.findByHash(expiredHash)).thenReturn(expired);
        when(apiKeyService.isValid(expired)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + expiredKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldReturn401ForDisabledApp() throws Exception {
        String keyForDisabledApp = apiKeyGenerator.generate();
        String hashForDisabledApp = apiKeyHasher.hash(keyForDisabledApp);

        AppEntity disabledApp = new AppEntity();
        disabledApp.setId(APP_ID);
        disabledApp.setUserId(USER_ID);
        disabledApp.setStatus("DISABLED");

        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(50L);
        key.setAppId(APP_ID);
        key.setUserId(USER_ID);
        key.setKeyHash(hashForDisabledApp);
        key.setStatus("ACTIVE");

        when(apiKeyService.findByHash(hashForDisabledApp)).thenReturn(key);
        when(apiKeyService.isValid(key)).thenReturn(true);
        when(appService.findById(APP_ID)).thenReturn(disabledApp);
        when(appService.isEnabled(disabledApp)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + keyForDisabledApp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_api_key");
    }

    @Test
    void shouldNotContainFullKeyInErrorResponse() throws Exception {
        String validPlaintext = setupValidKey();
        when(apiKeyService.findByHash(apiKeyHasher.hash(validPlaintext))).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        request.addHeader("Authorization", "Bearer " + validPlaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getContentAsString()).doesNotContain(validPlaintext);
    }

    @Test
    void shouldNotFilterNonV1Paths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldFilterV1Paths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotContainAdminEnvelopeInErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        String body = response.getContentAsString();
        assertThat(body).contains("\"error\"");
        assertThat(body).doesNotContain("\"code\":\"NOT_FOUND\"");
        assertThat(body).doesNotContain("\"data\"");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("java.");
    }

    @Test
    void authenticateShouldThrowForMalformedPrefix() {
        assertThatThrownBy(() -> filter.authenticate("sk-wrong-abc"))
                .isInstanceOf(GatewayAuthFilter.AuthFailureException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void authenticateShouldThrowForUnknownHash() {
        String unknownKey = apiKeyGenerator.generate();
        when(apiKeyService.findByHash(apiKeyHasher.hash(unknownKey))).thenReturn(null);

        assertThatThrownBy(() -> filter.authenticate(unknownKey))
                .isInstanceOf(GatewayAuthFilter.AuthFailureException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void authenticateShouldReturnContextForValidKey() {
        String validPlaintext = setupValidKey();

        GatewayRequestContext context = filter.authenticate(validPlaintext);

        assertThat(context.getAppId()).isEqualTo(APP_ID);
        assertThat(context.getUserId()).isEqualTo(USER_ID);
        assertThat(context.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(context.getApiKeyPrefix()).startsWith("sk-sangui-");
        assertThat(context.getApiKeyPrefix()).isNotEqualTo(validPlaintext);
    }
}
