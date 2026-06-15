package com.sangui.raggateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.auth.dto.AdminLoginDTO;
import com.sangui.raggateway.auth.vo.AdminLoginVO;
import com.sangui.raggateway.auth.vo.AdminUserVO;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AdminAuthController controller = new AdminAuthController(adminAuthService, userService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AdminAuthContextHolder.clear();
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        AdminJwtService.AdminJwtPayload payload = new AdminJwtService.AdminJwtPayload(100L, "admin", LocalDateTime.now().plusHours(24));
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setUsername("admin");
        user.setStatus("ACTIVE");

        AdminAuthService.AdminLoginResult loginResult =
                new AdminAuthService.AdminLoginResult("test-jwt-token", payload, user);
        when(adminAuthService.login("admin", "password123")).thenReturn(loginResult);

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.access_token").value("test-jwt-token"))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.user.id").value(100))
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    void shouldRejectLoginWithMissingUsername() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectLoginWithMissingPassword() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectLoginWithBlankFields() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectLoginWithWrongCredentials() throws Exception {
        when(adminAuthService.login("admin", "wrong-password")).thenReturn(null);

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnCurrentUserWithValidContext() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setUsername("admin");
        user.setStatus("ACTIVE");

        when(userService.findById(100L)).thenReturn(user);

        AdminAuthContextHolder.set(new AdminAuthContext(100L, "admin"));

        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectMeWithoutContext() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
