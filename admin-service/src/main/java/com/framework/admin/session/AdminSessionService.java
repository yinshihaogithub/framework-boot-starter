package com.framework.admin.session;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.support.AdminTextSupport;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.SessionManager;
import com.framework.core.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin-side online session management.
 */
@Slf4j
@Service
public class AdminSessionService {

    private final SessionManager sessionManager;
    private final AdminAuditService auditService;

    public AdminSessionService(SessionManager sessionManager, AdminAuditService auditService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    public List<SessionManager.OnlineSession> listSessions() {
        try {
            return sessionManager.listOnlineSessions();
        } catch (RuntimeException e) {
            log.warn("[在线会话] 会话列表查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public ActionResult<String> kickSession(Long userId, String deviceId, HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            return ActionResult.failure(ResultCode.PARAM_ERROR.getCode(), "用户ID必须大于0");
        }
        String safeDeviceId = text(deviceId);
        if (safeDeviceId == null) {
            return ActionResult.failure(ResultCode.PARAM_ERROR.getCode(), "设备不能为空");
        }
        if (isCurrentSession(userId, safeDeviceId)) {
            return ActionResult.failure(ResultCode.PARAM_ERROR.getCode(), "不能强制下线当前会话，请使用退出登录");
        }
        boolean exists;
        try {
            exists = sessionManager.listOnlineSessions().stream()
                    .anyMatch(session -> userId.equals(session.userId()) && safeDeviceId.equals(session.deviceId()));
        } catch (RuntimeException e) {
            log.warn("[在线会话] 强制下线前查询会话失败 userId={}, deviceId={}, error={}",
                    userId, safeDeviceId, e.getMessage());
            return ActionResult.failure(ResultCode.SERVICE_ERROR.getCode(), "会话查询失败");
        }
        if (!exists) {
            return ActionResult.failure(ResultCode.NOT_FOUND.getCode(), "会话不存在或已失效");
        }
        try {
            sessionManager.forceLogout(userId, safeDeviceId);
        } catch (RuntimeException e) {
            log.warn("[在线会话] 强制下线失败 userId={}, deviceId={}, error={}",
                    userId, safeDeviceId, e.getMessage());
            return ActionResult.failure(ResultCode.SERVICE_ERROR.getCode(), "强制下线失败");
        }
        auditSuccess(request, "强制下线", "DELETE", "userId", userId, "deviceId", safeDeviceId);
        return ActionResult.success("已强制下线");
    }

    private void auditSuccess(HttpServletRequest request, String action, String operationType, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(request, "在线会话", action, operationType, auditService.params(params));
        } catch (RuntimeException e) {
            log.warn("[在线会话] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private boolean isCurrentSession(Long userId, String deviceId) {
        LoginUser current = UserContextHolder.get();
        return current != null
                && userId.equals(current.getUserId())
                && deviceId.equals(current.getDeviceId());
    }

    private String text(String value) {
        return AdminTextSupport.trimToNull(value);
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
