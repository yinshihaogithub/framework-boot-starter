package com.framework.admin.session;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.SessionManager;
import com.framework.core.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin-side online session management.
 */
@Service
public class AdminSessionService {

    private final SessionManager sessionManager;
    private final AdminAuditService auditService;

    public AdminSessionService(SessionManager sessionManager, AdminAuditService auditService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    public List<SessionManager.OnlineSession> listSessions() {
        return sessionManager.listOnlineSessions();
    }

    public ActionResult<String> kickSession(Long userId, String deviceId, HttpServletRequest request) {
        if (userId == null || isBlank(deviceId)) {
            return ActionResult.failure(ResultCode.PARAM_ERROR.getCode(), "用户和设备不能为空");
        }
        if (isCurrentSession(userId, deviceId)) {
            return ActionResult.failure(ResultCode.PARAM_ERROR.getCode(), "不能强制下线当前会话，请使用退出登录");
        }
        boolean exists = sessionManager.listOnlineSessions().stream()
                .anyMatch(session -> userId.equals(session.userId()) && deviceId.equals(session.deviceId()));
        if (!exists) {
            return ActionResult.failure(ResultCode.NOT_FOUND.getCode(), "会话不存在或已失效");
        }
        sessionManager.forceLogout(userId, deviceId);
        auditService.success(request, "在线会话", "强制下线", "DELETE",
                auditService.params("userId", userId, "deviceId", deviceId));
        return ActionResult.success("已强制下线");
    }

    private boolean isCurrentSession(Long userId, String deviceId) {
        LoginUser current = UserContextHolder.get();
        return current != null
                && userId.equals(current.getUserId())
                && deviceId.equals(current.getDeviceId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ActionResult<T>(boolean success, int code, String message, T data) {

        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
        }

        public static <T> ActionResult<T> failure(int code, String message) {
            return new ActionResult<>(false, code, message, null);
        }
    }
}
