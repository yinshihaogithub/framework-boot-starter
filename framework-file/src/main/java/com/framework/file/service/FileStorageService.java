package com.framework.file.service;

import com.framework.file.model.StoredFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * File storage abstraction.
 */
public interface FileStorageService {

    StoredFile store(String originalFilename, InputStream inputStream) throws IOException;

    InputStream load(String key) throws IOException;

    void delete(String key) throws IOException;
}
