package com.framework.admin.file;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/files")
@Tag(name = "文件中心", description = "文件上传、下载和元数据管理")
@RequirePermission("file:view")
public class FileAdminController {

    private final FileAdminService fileAdminService;

    public FileAdminController(FileAdminService fileAdminService) {
        this.fileAdminService = fileAdminService;
    }

    @Operation(summary = "文件统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(fileAdminService.stats());
    }

    @Operation(summary = "文件列表")
    @GetMapping
    public Result<PageResult<FileAdminModels.FileRecord>> list(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String businessType,
                                                               @RequestParam(required = false) String businessKey,
                                                               @RequestParam(required = false) String contentType,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(fileAdminService.list(keyword, businessType, businessKey, contentType, pageNum, pageSize));
    }

    @Operation(summary = "上传文件")
    @PostMapping
    @RequirePermission("file:upload")
    public Result<FileAdminModels.FileRecord> upload(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(required = false) String businessType,
                                                     @RequestParam(required = false) String businessKey,
                                                     HttpServletRequest servletRequest) {
        return fileAdminService.upload(file, businessType, businessKey, servletRequest);
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        Result<ResponseEntity<Resource>> result = fileAdminService.download(id);
        if (result.isSuccess()) {
            return result.getData();
        }
        return ResponseEntity.status(downloadErrorStatus(result.getCode()))
                .body(Result.fail(result.getCode(), result.getMessage()));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    @RequirePermission("file:delete")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        return fileAdminService.delete(id, servletRequest);
    }

    private HttpStatus downloadErrorStatus(int code) {
        if (code == ResultCode.PARAM_ERROR.getCode() || code == ResultCode.BAD_REQUEST.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ResultCode.NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
