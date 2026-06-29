package com.framework.admin.file;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.file.model.StoredFile;
import com.framework.file.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class FileAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final FileAdminRepository repository;
    private final ObjectProvider<FileStorageService> fileStorageServiceProvider;
    private final AdminAuditService auditService;

    public FileAdminService(FileAdminRepository repository,
                            ObjectProvider<FileStorageService> fileStorageServiceProvider,
                            AdminAuditService auditService) {
        this.repository = repository;
        this.fileStorageServiceProvider = fileStorageServiceProvider;
        this.auditService = auditService;
    }

    public Map<String, Long> stats() {
        try {
            return repository.stats();
        } catch (RuntimeException e) {
            log.warn("[文件中心] 统计查询失败 error={}", e.getMessage());
            return emptyStats();
        }
    }

    public PageResult<FileAdminModels.FileRecord> list(String keyword, String businessType, String businessKey,
                                                       String contentType, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        try {
            return PageResult.of(
                    repository.list(keyword, businessType, businessKey, contentType, safePageNum, safePageSize),
                    repository.count(keyword, businessType, businessKey, contentType),
                    safePageNum,
                    safePageSize);
        } catch (RuntimeException e) {
            log.warn("[文件中心] 文件列表查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public Result<FileAdminModels.FileRecord> upload(MultipartFile file, String businessType, String businessKey,
                                                     HttpServletRequest servletRequest) {
        if (file == null || file.isEmpty()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "文件不能为空");
        }
        FileStorageService storageService = available(fileStorageServiceProvider);
        if (storageService == null) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件存储服务未启用");
        }
        try (InputStream inputStream = file.getInputStream()) {
            StoredFile storedFile = storageService.store(file.getOriginalFilename(), inputStream);
            try {
                FileAdminModels.FileRecord record = repository.create(new FileAdminModels.FileRecord()
                        .setFileKey(storedFile.getKey())
                        .setOriginalFilename(storedFile.getOriginalFilename())
                        .setContentType(storedFile.getContentType())
                        .setFileSize(storedFile.getSize())
                        .setUrl(storedFile.getUrl())
                        .setStorageType("LOCAL")
                        .setBusinessType(text(businessType))
                        .setBusinessKey(text(businessKey))
                        .setOperatorId(UserContextHolder.getUserId())
                        .setOperatorName(UserContextHolder.getUsername())
                        .setDeleted(false));
                auditService.success(servletRequest, "文件中心", "上传文件", "CREATE",
                        auditService.params("fileId", record.getId(), "filename", record.getOriginalFilename(),
                                "businessType", record.getBusinessType(), "businessKey", record.getBusinessKey()));
                return Result.success(record);
            } catch (RuntimeException e) {
                cleanupStoredFile(storageService, storedFile);
                log.warn("[文件中心] 文件上传元数据保存失败 filename={}, fileKey={}, error={}",
                        file.getOriginalFilename(), storedFile.getKey(), e.getMessage());
                return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件上传失败");
            }
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
        } catch (IOException e) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件上传失败");
        } catch (RuntimeException e) {
            log.warn("[文件中心] 文件上传失败 filename={}, error={}", file.getOriginalFilename(), e.getMessage());
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件上传失败");
        }
    }

    public Result<ResponseEntity<Resource>> download(Long id) {
        FileAdminModels.FileRecord record;
        try {
            record = repository.findById(id).orElse(null);
        } catch (RuntimeException e) {
            log.warn("[文件中心] 文件元数据查询失败 fileId={}, error={}", id, e.getMessage());
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件读取失败");
        }
        if (record == null || Boolean.TRUE.equals(record.getDeleted())) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "文件不存在");
        }
        FileStorageService storageService = available(fileStorageServiceProvider);
        if (storageService == null) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件存储服务未启用");
        }
        try {
            InputStream inputStream = storageService.load(record.getFileKey());
            InputStreamResource resource = new InputStreamResource(inputStream);
            MediaType mediaType = MediaType.parseMediaType(text(record.getContentType(), "application/octet-stream"));
            ResponseEntity<Resource> response = ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(record.getFileSize() == null ? -1L : record.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(text(record.getOriginalFilename(), "file"), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
            return Result.success(response);
        } catch (IOException e) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件读取失败");
        } catch (RuntimeException e) {
            log.warn("[文件中心] 文件读取失败 fileId={}, fileKey={}, error={}",
                    id, record.getFileKey(), e.getMessage());
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件读取失败");
        }
    }

    public Result<String> delete(Long id, HttpServletRequest servletRequest) {
        FileAdminModels.FileRecord record;
        try {
            record = repository.findById(id).orElse(null);
        } catch (RuntimeException e) {
            log.warn("[文件中心] 删除前查询文件失败 fileId={}, error={}", id, e.getMessage());
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件删除失败");
        }
        if (record == null || Boolean.TRUE.equals(record.getDeleted())) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "文件不存在");
        }
        FileStorageService storageService = available(fileStorageServiceProvider);
        if (storageService == null) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件存储服务未启用");
        }
        try {
            repository.markDeleted(id);
        } catch (RuntimeException e) {
            log.warn("[文件中心] 文件元数据删除失败 fileId={}, error={}", id, e.getMessage());
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "文件删除失败");
        }
        boolean physicalDeleted = true;
        try {
            storageService.delete(record.getFileKey());
        } catch (IOException e) {
            physicalDeleted = false;
            log.warn("[文件中心] 物理文件删除失败 fileId={}, fileKey={}, error={}",
                    id, record.getFileKey(), e.getMessage());
        } catch (RuntimeException e) {
            physicalDeleted = false;
            log.warn("[文件中心] 物理文件删除失败 fileId={}, fileKey={}, error={}",
                    id, record.getFileKey(), e.getMessage());
        }
        auditService.success(servletRequest, "文件中心", "删除文件", "DELETE",
                auditService.params("fileId", id, "filename", record.getOriginalFilename(),
                        "fileKey", record.getFileKey(), "physicalDeleted", physicalDeleted));
        return Result.success(physicalDeleted ? "已删除" : "已删除，物理文件待清理");
    }

    private <T> T available(ObjectProvider<T> provider) {
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[文件中心] 依赖服务获取失败 type={}, error={}",
                    provider.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private Map<String, Long> emptyStats() {
        return Map.of("active", 0L, "deleted", 0L, "totalSize", 0L);
    }

    private void cleanupStoredFile(FileStorageService storageService, StoredFile storedFile) {
        try {
            storageService.delete(storedFile.getKey());
        } catch (IOException | RuntimeException cleanupFailure) {
            log.warn("[文件中心] 上传失败回滚物理文件失败 fileKey={}, error={}",
                    storedFile.getKey(), cleanupFailure.getMessage());
        }
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String text(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }
}
