package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "后台认证", description = "登录、退出和当前用户")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "后台登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, clientIp(servletRequest));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<CurrentUser> me() {
        return authService.me();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = FrameworkConstants.AUTH_HEADER, required = false) String authorization) {
        return authService.logout(authorization);
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody ChangePasswordRequest request,
                                         HttpServletRequest servletRequest) {
        return authService.changePassword(request, servletRequest);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String deviceId;
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class LoginResponse {
        private String accessToken;
        private CurrentUser user;
        private List<Menu> menus;

        public LoginResponse setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public LoginResponse setUser(CurrentUser user) {
            this.user = user;
            return this;
        }

        public LoginResponse setMenus(List<Menu> menus) {
            this.menus = menus;
            return this;
        }
    }

    @Data
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String nickname;
        private Long tenantId;
        private List<String> roles;
        private List<String> permissions;
        private List<Menu> menus;

        public CurrentUser setUserId(Long userId) {
            this.userId = userId;
            return this;
        }

        public CurrentUser setUsername(String username) {
            this.username = username;
            return this;
        }

        public CurrentUser setNickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public CurrentUser setTenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public CurrentUser setRoles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public CurrentUser setPermissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public CurrentUser setMenus(List<Menu> menus) {
            this.menus = menus;
            return this;
        }
    }
}
