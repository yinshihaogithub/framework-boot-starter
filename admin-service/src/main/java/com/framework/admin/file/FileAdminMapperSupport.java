package com.framework.admin.file;

import com.framework.admin.support.AdminTextSupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FileAdminMapperSupport {

    private FileAdminMapperSupport() {
    }

    static List<FileAdminModels.FileRecord> list(FileAdminMapper mapper,
                                                 String keyword,
                                                 String businessType,
                                                 String businessKey,
                                                 String contentType,
                                                 int pageNum,
                                                 int pageSize) {
        return mapper.list(like(keyword), text(businessType), like(businessKey), like(contentType),
                offset(pageNum, pageSize), pageSize);
    }

    static long count(FileAdminMapper mapper, String keyword, String businessType, String businessKey,
                      String contentType) {
        return mapper.count(like(keyword), text(businessType), like(businessKey), like(contentType));
    }

    static Map<String, Long> stats(FileAdminMapper mapper) {
        return Map.of(
                "active", mapper.countActive(),
                "deleted", mapper.countDeleted(),
                "totalSize", mapper.sumActiveSize());
    }

    static FileAdminModels.FileRecord create(FileAdminMapper mapper, FileAdminModels.FileRecord record) {
        mapper.insert(record);
        return record;
    }

    static Optional<FileAdminModels.FileRecord> findById(FileAdminMapper mapper, Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    static boolean markDeleted(FileAdminMapper mapper, Long id) {
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
