package com.framework.file.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Stored file descriptor.
 */
@Data
@AllArgsConstructor
public class StoredFile {

    private String key;
    private String originalFilename;
    private long size;
    private String url;
    private String contentType;
}
