package com.sangui.raggateway.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import jakarta.servlet.FilterChain;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthFilterTest {

    @Mock
    private UserService userService;

    @Mock
    private FilterChain filterChain;

    private AdminJwtService adminJwtService;
    private AdminAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminJwtService = new AdminJwtService("test-secret-key-for-jwt-signing-min-256-bits!!", 3600);
        objectMapper = new ObjectMapper();
        filter = new AdminAuthFilter(adminJwtService, userService, objectMapper);

        UserEntity activeUser = new UserEntity();
        activeUser.setId(100L);
        activeUser.setUsername("admin");
        activeUser.setStatus("ACTIVE");
        when(userService.findById(100L)).thenReturn(activeUser);
        when(userService.isActive(activeUser)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        AdminAuthContextHolder.clear();
    }

    @Test
    void shouldNotFilterLoginPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/auth/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldNotFilterNonAdminPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/models");

        assertThat(filter.shouldNotFilter(request)).isTrue();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldNotFilterRootPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/some/other/path");

        assertThat(filter.shouldNotFilter(request)).isTrue();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldFilterAdminPathExceptLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/auth/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn401WhenMissingAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenNonBearerScheme() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenEmptyToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldProceedWithValidToken() throws Exception {
        String token = adminJwtService.createToken(100L, "admin");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetAndClearContext() throws Exception {
        String token = adminJwtService.createToken(100L, "admin");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        AdminAuthContext context = AdminAuthContextHolder.get();
        assertThat(context).isNull();
    }

    @Test
    void shouldReturn401WhenUserDisabled() throws Exception {
        UserEntity disabledUser = new UserEntity();
        disabledUser.setId(200L);
        disabledUser.setUsername("disabled");
        disabledUser.setStatus("DISABLED");
        when(userService.findById(200L)).thenReturn(disabledUser);

        AdminJwtService altService = new AdminJwtService("test-secret-key-for-jwt-signing-min-256-bits!!", 3600);
        String token = altService.createToken(200L, "disabled");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenUserNotFound() throws Exception {
        when(userService.findById(999L)).thenReturn(null);

        AdminJwtService altService = new AdminJwtService("test-secret-key-for-jwt-signing-min-256-bits!!", 3600);
        String token = altService.createToken(999L, "ghost");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/apps");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }
}
