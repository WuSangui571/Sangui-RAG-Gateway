package com.sangui.raggateway.auth;

import com.sangui.raggateway.auth.dto.AdminLoginDTO;
import com.sangui.raggateway.auth.vo.AdminLoginVO;
import com.sangui.raggateway.auth.vo.AdminUserVO;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Profile("!test")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final UserService userService;

    public AdminAuthController(AdminAuthService adminAuthService, UserService userService) {
        this.adminAuthService = adminAuthService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@RequestBody AdminLoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "username and password are required");
        }

        AdminAuthService.AdminLoginResult loginResult = adminAuthService.login(
                dto.getUsername().trim(), dto.getPassword());
        if (loginResult == null) {
            throw new BusinessException("UNAUTHORIZED", "Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = loginResult.getUser();

        AdminUserVO userVO = new AdminUserVO(user.getId(), user.getUsername(), user.getStatus());
        AdminLoginVO loginVO = new AdminLoginVO(
                loginResult.getToken(), "Bearer", loginResult.getPayload().getExpiresAt(), userVO);
        return ApiResponse.success(loginVO);
    }

    @GetMapping("/me")
    public ApiResponse<AdminUserVO> me() {
        AdminAuthContext ctx = AdminAuthContextHolder.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = userService.findById(ctx.getUserId());
        if (user == null) {
            throw new BusinessException("UNAUTHORIZED", "User not found", HttpStatus.UNAUTHORIZED);
        }

        AdminUserVO vo = new AdminUserVO(user.getId(), user.getUsername(), user.getStatus());
        return ApiResponse.success(vo);
    }
}
