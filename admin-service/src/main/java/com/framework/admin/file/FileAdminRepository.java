package com.framework.admin.file;

import com.framework.admin.support.AdminTextSupport;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FileAdminRepository {

    private final FileAdminMapper mapper;

    public FileAdminRepository(FileAdminMapper mapper) {
        this.mapper = mapper;
    }

    public List<FileAdminModels.FileRecord> list(String keyword, String businessType, String businessKey,
                                                 String contentType, int pageNum, int pageSize) {
        return mapper.list(like(keyword), text(businessType), like(businessKey), like(contentType),
                offset(pageNum, pageSize), pageSize);
    }

    public long count(String keyword, String businessType, String businessKey, String contentType) {
        return mapper.count(like(keyword), text(businessType), like(businessKey), like(contentType));
    }

    public Map<String, Long> stats() {
        return Map.of(
                "active", mapper.countActive(),
                "deleted", mapper.countDeleted(),
                "totalSize", mapper.sumActiveSize());
    }

    public FileAdminModels.FileRecord create(FileAdminModels.FileRecord record) {
        mapper.insert(record);
        return record;
    }

    public Optional<FileAdminModels.FileRecord> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public boolean markDeleted(Long id) {
        return mapper.markDeleted(id) > 0;
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String text(String value) {
        return AdminTextSupport.trimToNull(value);
    }

    private static String like(String value) {
        String text = text(value);
        return text == null ? null : "%" + text + "%";
    }
}
