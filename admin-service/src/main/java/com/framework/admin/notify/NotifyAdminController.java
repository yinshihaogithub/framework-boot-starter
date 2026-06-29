package com.framework.admin.notify;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/notify")
@Tag(name = "通知中心", description = "通知模板、发送测试和发送记录")
@RequirePermission("notify:view")
public class NotifyAdminController {

    private final NotifyAdminService notifyAdminService;

    public NotifyAdminController(NotifyAdminService notifyAdminService) {
        this.notifyAdminService = notifyAdminService;
    }

    @Operation(summary = "通知统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(notifyAdminService.stats());
    }

    @Operation(summary = "通知模板列表")
    @GetMapping("/templates")
    public Result<PageResult<NotifyAdminModels.Template>> templates(@RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) String channel,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(notifyAdminService.templates(keyword, channel, status, pageNum, pageSize));
    }

    @Operation(summary = "新增通知模板")
    @PostMapping("/templates")
    @RequirePermission("notify:template:create")
    public Result<Long> createTemplate(@RequestBody NotifyAdminModels.TemplateRequest request,
                                       HttpServletRequest servletRequest) {
        return toResult(notifyAdminService.createTemplate(request, servletRequest));
    }

    @Operation(summary = "更新通知模板")
    @PutMapping("/templates/{id}")
    @RequirePermission("notify:template:update")
    public Result<String> updateTemplate(@PathVariable Long id,
                                         @RequestBody NotifyAdminModels.TemplateRequest request,
                                         HttpServletRequest servletRequest) {
        return toResult(notifyAdminService.updateTemplate(id, request, servletRequest));
    }

    @Operation(summary = "删除通知模板")
    @DeleteMapping("/templates/{id}")
    @RequirePermission("notify:template:delete")
    public Result<String> deleteTemplate(@PathVariable Long id, HttpServletRequest servletRequest) {
        return toResult(notifyAdminService.deleteTemplate(id, servletRequest));
    }

    @Operation(summary = "发送测试通知")
    @PostMapping("/templates/{id}/send-test")
    @RequirePermission("notify:send-test")
    public Result<NotifyAdminModels.Record> sendTest(@PathVariable Long id,
                                                     @RequestBody(required = false) NotifyAdminModels.SendRequest request,
                                                     HttpServletRequest servletRequest) {
        return toResult(notifyAdminService.sendTest(id, request, servletRequest));
    }

    @Operation(summary = "通知发送记录")
    @GetMapping("/records")
    public Result<PageResult<NotifyAdminModels.Record>> records(@RequestParam(required = false) String channel,
                                                                @RequestParam(required = false) Boolean success,
                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(notifyAdminService.records(channel, success, pageNum, pageSize));
    }

    private <T> Result<T> toResult(NotifyAdminService.ActionResult<T> result) {
        return result.success() ? Result.success(result.data()) : Result.fail(result.code(), result.message());
    }
}
