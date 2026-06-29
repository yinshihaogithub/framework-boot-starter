package com.framework.admin.file;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.file.model.StoredFile;
import com.framework.file.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FileAdminServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final FakeStorageService storageService = new FakeStorageService();
    private final FakeAuditService auditService = new FakeAuditService();
    private final FileAdminService service = new FileAdminService(repository, provider(storageService), auditService);

    @Test
    void listSanitizesPagingAndReturnsStats() {
        repository.records = List.of(new FileAdminModels.FileRecord()
                .setId(1L)
                .setOriginalFilename("a.txt"));
        repository.count = 1;
        repository.stats = Map.of("active", 1L, "deleted", 0L, "totalSize", 3L);

        PageResult<FileAdminModels.FileRecord> page = service.list(" a ", "system", "order-1", " text ", -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(repository.listPageNum).isEqualTo(1);
        assertThat(repository.listPageSize).isEqualTo(200);
        assertThat(repository.listKeyword).isEqualTo(" a ");
        assertThat(repository.listBusinessType).isEqualTo("system");
        assertThat(repository.listBusinessKey).isEqualTo("order-1");
        assertThat(repository.listContentType).isEqualTo(" text ");
        assertThat(service.stats()).containsEntry("totalSize", 3L);
    }

    @Test
    void uploadStoresFileMetadataAndWritesAudit() {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "abc".getBytes());

        Result<FileAdminModels.FileRecord> result = service.upload(file, "system", "user-1", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(9L);
        assertThat(result.getData().getOriginalFilename()).isEqualTo("hello.txt");
        assertThat(result.getData().getBusinessType()).isEqualTo("system");
        assertThat(result.getData().getBusinessKey()).isEqualTo("user-1");
        assertThat(storageService.storedFilename).isEqualTo("hello.txt");
        assertThat(repository.created.getFileKey()).isEqualTo("file-key");
        assertThat(auditService.actions).containsExactly("上传文件");
    }

    @Test
    void uploadSucceedsWhenAuditFails() {
        FileAdminService serviceWithAuditFailure = new FileAdminService(repository, provider(storageService),
                new ThrowingAuditService());
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "abc".getBytes());

        Result<FileAdminModels.FileRecord> result = serviceWithAuditFailure.upload(file, "system", "user-1", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(9L);
        assertThat(repository.created.getFileKey()).isEqualTo("file-key");
        assertThat(storageService.deletedKey).isNull();
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        Result<FileAdminModels.FileRecord> result = service.upload(file, null, null, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(repository.created).isNull();
    }

    @Test
    void downloadReturnsStoredResource() throws Exception {
        repository.record = new FileAdminModels.FileRecord()
                .setId(9L)
                .setFileKey("file-key")
                .setOriginalFilename("hello.txt")
                .setContentType("text/plain")
                .setFileSize(3L);
        storageService.bytes = "abc".getBytes();

        Result<ResponseEntity<Resource>> result = service.download(9L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getHeaders().getContentDisposition().getFilename()).isEqualTo("hello.txt");
        assertThat(result.getData().getBody().getInputStream().readAllBytes()).isEqualTo("abc".getBytes());
    }

    @Test
    void downloadRejectsInvalidIdBeforeRepositoryLookup() {
        repository.findFailure = new RuntimeException("database down");

        Result<ResponseEntity<Resource>> result = service.download(0L);

        assertInvalidFileId(result);
    }

    @Test
    void deleteRemovesStorageAndMarksMetadataDeleted() {
        repository.record = new FileAdminModels.FileRecord()
                .setId(9L)
                .setFileKey("file-key")
                .setOriginalFilename("hello.txt");

        Result<String> result = service.delete(9L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(storageService.deletedKey).isEqualTo("file-key");
        assertThat(repository.deletedId).isEqualTo(9L);
        assertThat(auditService.actions).containsExactly("删除文件");
        assertThat(auditService.params).containsEntry("physicalDeleted", true);
    }

    @Test
    void deleteSucceedsWhenAuditFails() {
        FileAdminService serviceWithAuditFailure = new FileAdminService(repository, provider(storageService),
                new ThrowingAuditService());
        repository.record = new FileAdminModels.FileRecord()
                .setId(9L)
                .setFileKey("file-key")
                .setOriginalFilename("hello.txt");

        Result<String> result = serviceWithAuditFailure.delete(9L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已删除");
        assertThat(storageService.deletedKey).isEqualTo("file-key");
        assertThat(repository.deletedId).isEqualTo(9L);
    }

    @Test
    void deleteKeepsMetadataDeletedWhenPhysicalCleanupFails() {
        repository.record = new FileAdminModels.FileRecord()
                .setId(10L)
                .setFileKey("missing-file-key")
                .setOriginalFilename("stale.txt");
        storageService.deleteFailure = new IOException("disk unavailable");

        Result<String> result = service.delete(10L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已删除，物理文件待清理");
        assertThat(repository.deletedId).isEqualTo(10L);
        assertThat(storageService.deletedKey).isEqualTo("missing-file-key");
        assertThat(auditService.params).containsEntry("physicalDeleted", false);
    }

    @Test
    void deleteRejectsInvalidIdBeforeRepositoryLookup() {
        repository.findFailure = new RuntimeException("database down");

        Result<String> result = service.delete(0L, null);

        assertInvalidFileId(result);
        assertThat(storageService.deletedKey).isNull();
        assertThat(repository.deletedId).isNull();
    }

    @Test
    void queryEndpointsFallBackWhenRepositoryFails() {
        repository.statsFailure = new RuntimeException("database down");
        repository.listFailure = new RuntimeException("database down");

        assertThat(service.stats())
                .containsEntry("active", 0L)
                .containsEntry("deleted", 0L)
                .containsEntry("totalSize", 0L);

        PageResult<FileAdminModels.FileRecord> page = service.list(null, null, null, null, 0, 0);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    void uploadReportsUnavailableWhenStorageProviderFails() {
        FileAdminService unavailableService = new FileAdminService(repository, failingProvider(), auditService);
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "abc".getBytes());

        Result<FileAdminModels.FileRecord> result = unavailableService.upload(file, null, null, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("文件存储服务未启用");
        assertThat(repository.created).isNull();
    }

    @Test
    void uploadReportsServiceErrorWhenMetadataCreateFails() {
        repository.createFailure = new RuntimeException("database down");
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "abc".getBytes());

        Result<FileAdminModels.FileRecord> result = service.upload(file, null, null, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("文件上传失败");
        assertThat(storageService.storedFilename).isEqualTo("hello.txt");
        assertThat(storageService.deletedKey).isEqualTo("file-key");
    }

    @Test
    void downloadReportsServiceErrorWhenRepositoryFails() {
        repository.findFailure = new RuntimeException("database down");

        Result<ResponseEntity<Resource>> result = service.download(9L);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("文件读取失败");
    }

    @Test
    void deleteReportsServiceErrorWhenMarkDeletedFails() {
        repository.record = new FileAdminModels.FileRecord()
                .setId(9L)
                .setFileKey("file-key")
                .setOriginalFilename("hello.txt");
        repository.markFailure = new RuntimeException("database down");

        Result<String> result = service.delete(9L, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("文件删除失败");
        assertThat(storageService.deletedKey).isNull();
        assertThat(auditService.actions).isEmpty();
    }

    private static void assertInvalidFileId(Result<?> result) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("文件ID必须大于0");
    }

    private static class FakeRepository extends FileAdminRepository {
        private List<FileAdminModels.FileRecord> records = List.of();
        private long count;
        private int listPageNum;
        private int listPageSize;
        private String listKeyword;
        private String listBusinessType;
        private String listBusinessKey;
        private String listContentType;
        private Map<String, Long> stats = Map.of("active", 0L, "deleted", 0L, "totalSize", 0L);
        private FileAdminModels.FileRecord created;
        private FileAdminModels.FileRecord record;
        private Long deletedId;
        private RuntimeException statsFailure;
        private RuntimeException listFailure;
        private RuntimeException createFailure;
        private RuntimeException findFailure;
        private RuntimeException markFailure;

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<FileAdminModels.FileRecord> list(String keyword, String businessType, String businessKey,
                                                     String contentType, int pageNum, int pageSize) {
            if (listFailure != null) {
                throw listFailure;
            }
            this.listKeyword = keyword;
            this.listBusinessType = businessType;
            this.listBusinessKey = businessKey;
            this.listContentType = contentType;
            this.listPageNum = pageNum;
            this.listPageSize = pageSize;
            return records;
        }

        @Override
        public long count(String keyword, String businessType, String businessKey, String contentType) {
            return count;
        }

        @Override
        public Map<String, Long> stats() {
            if (statsFailure != null) {
                throw statsFailure;
            }
            return stats;
        }

        @Override
        public FileAdminModels.FileRecord create(FileAdminModels.FileRecord record) {
            if (createFailure != null) {
                throw createFailure;
            }
            this.created = record.setId(9L);
            return created;
        }

        @Override
        public Optional<FileAdminModels.FileRecord> findById(Long id) {
            if (findFailure != null) {
                throw findFailure;
            }
            return Optional.ofNullable(record);
        }

        @Override
        public void markDeleted(Long id) {
            if (markFailure != null) {
                throw markFailure;
            }
            this.deletedId = id;
        }
    }

    private static class FakeStorageService implements FileStorageService {
        private String storedFilename;
        private byte[] bytes = new byte[0];
        private String deletedKey;
        private IOException deleteFailure;

        @Override
        public StoredFile store(String originalFilename, InputStream inputStream) throws IOException {
            this.storedFilename = originalFilename;
            this.bytes = inputStream.readAllBytes();
            return new StoredFile("file-key", originalFilename, bytes.length, "/files/file-key", "text/plain");
        }

        @Override
        public InputStream load(String key) {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(String key) throws IOException {
            this.deletedKey = key;
            if (deleteFailure != null) {
                throw deleteFailure;
            }
        }
    }

    private static class FakeAuditService extends AdminAuditService {
        private final List<String> actions = new ArrayList<>();
        private Map<String, Object> params;

        private FakeAuditService() {
            super(null, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            actions.add(action);
            this.params = (Map<String, Object>) params;
        }
    }

    private static class ThrowingAuditService extends AdminAuditService {

        private ThrowingAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            throw new IllegalStateException("audit unavailable");
        }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }

    private static <T> ObjectProvider<T> failingProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public Stream<T> stream() {
                throw new IllegalStateException("provider failed");
            }
        };
    }
}
