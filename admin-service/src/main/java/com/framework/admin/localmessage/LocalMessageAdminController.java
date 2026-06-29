package com.framework.admin.localmessage;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地消息表后台管理接口。
 */
@RestController
@RequestMapping("/admin/local-messages")
@Tag(name = "本地消息管理", description = "本地消息表与人工补偿")
@RequirePermission("local-message:view")
public class LocalMessageAdminController {

    private final LocalMessageAdminService localMessageAdminService;

    public LocalMessageAdminController(LocalMessageAdminService localMessageAdminService) {
        this.localMessageAdminService = localMessageAdminService;
    }

    @Operation(summary = "本地消息统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(localMessageAdminService.stats());
    }

    @Operation(summary = "本地消息列表")
    @GetMapping
    public Result<PageResult<LocalMessageVO>> list(@RequestParam(required = false) String topic,
                                                   @RequestParam(required = false) LocalMessageStatus status,
                                                   @RequestParam(required = false) String traceId,
                                                   @RequestParam(required = false) String businessKey,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(localMessageAdminService.list(topic, status, traceId, businessKey, pageNum, pageSize));
    }

    @Operation(summary = "本地消息详情")
    @GetMapping("/{id}")
    public Result<LocalMessageVO> detail(@PathVariable Long id) {
        return toResult(localMessageAdminService.detail(id));
    }

    @Operation(summary = "扫描并重试到期消息")
    @PostMapping("/retry-due")
    @RequirePermission("local-message:retry")
    public Result<Integer> retryDueMessages(HttpServletRequest servletRequest) {
        return toResult(localMessageAdminService.retryDueMessages(servletRequest));
    }

    @Operation(summary = "标记成功")
    @PostMapping("/{id}/success")
    @RequirePermission("local-message:retry")
    public Result<String> markSuccess(@PathVariable Long id, HttpServletRequest servletRequest) {
        return toResult(localMessageAdminService.markSuccess(id, servletRequest));
    }

    @Operation(summary = "标记失败")
    @PostMapping("/{id}/failure")
    @RequirePermission("local-message:retry")
    public Result<String> markFailure(@PathVariable Long id,
                                      @RequestBody(required = false) FailureRequest request,
                                      HttpServletRequest servletRequest) {
        String reason = request == null ? null : request.getReason();
        return toResult(localMessageAdminService.markFailure(id, reason, servletRequest));
    }

    @Operation(summary = "删除本地消息")
    @DeleteMapping("/{id}")
    @RequirePermission("local-message:retry")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        return toResult(localMessageAdminService.delete(id, servletRequest));
    }

    private <T> Result<T> toResult(LocalMessageAdminService.ActionResult<T> result) {
        return result.success() ? Result.success(result.data()) : Result.fail(result.code(), result.message());
    }

    @Data
    public static class FailureRequest {
        private String reason;
    }
}
