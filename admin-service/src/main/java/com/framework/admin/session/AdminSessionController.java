package com.framework.admin.session;

import com.framework.auth.service.SessionManager;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Online session management endpoints.
 */
@RestController
@RequestMapping("/admin/sessions")
@Tag(name = "在线会话", description = "后台在线会话查看与强制下线")
@RequirePermission("session:view")
public class AdminSessionController {

    private final AdminSessionService sessionService;

    public AdminSessionController(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "在线会话列表")
    @GetMapping
    public Result<PageResult<SessionManager.OnlineSession>> listSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(sessionService.listSessions(pageNum, pageSize));
    }

    @Operation(summary = "强制下线会话")
    @DeleteMapping("/{userId}/{deviceId}")
    @RequirePermission("session:kick")
    public Result<String> kickSession(@PathVariable Long userId,
                                      @PathVariable String deviceId,
                                      HttpServletRequest request) {
        AdminSessionService.ActionResult<String> result = sessionService.kickSession(userId, deviceId, request);
        return result.success()
                ? Result.success(result.data())
                : Result.fail(result.code(), result.message());
    }
}
