package com.framework.file.service;

import com.framework.file.config.FileProperties;
import com.framework.file.model.StoredFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Local filesystem storage implementation.
 */
public class LocalFileStorageService implements FileStorageService {

    private final FileProperties properties;

    public LocalFileStorageService(FileProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile store(String originalFilename, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
        Path basePath = basePath();
        Files.createDirectories(basePath);
        String safeFilename = sanitize(originalFilename);
        validateExtension(safeFilename);
        String key = UUID.randomUUID() + "-" + safeFilename;
        Path target = resolveKey(key);
        try {
            long size = copyWithLimit(inputStream, target);
            String contentType = contentType(target, safeFilename);
            return new StoredFile(key, originalFilename, size, publicUrl(key), contentType);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    @Override
    public InputStream load(String key) throws IOException {
        return Files.newInputStream(resolveKey(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolveKey(key));
    }

    private String sanitize(String value) {
        String filename = value == null || value.isBlank() ? "file" : Path.of(value).getFileName().toString();
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path basePath() {
        return Path.of(properties.getBasePath()).toAbsolutePath().normalize();
    }

    private Path resolveKey(String key) {
        Path basePath = basePath();
        Path target = basePath.resolve(sanitize(key)).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalArgumentException("file key is not allowed");
        }
        return target;
    }

    private void validateExtension(String filename) {
        if (properties.getAllowedExtensions() == null || properties.getAllowedExtensions().isEmpty()) {
            return;
        }
        String extension = extension(filename);
        boolean allowed = properties.getAllowedExtensions().stream()
                .map(this::normalizeExtension)
                .anyMatch(extension::equals);
        if (!allowed) {
            throw new IllegalArgumentException("file extension is not allowed: " + extension);
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String value = extension.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value.substring(1) : value;
    }

    private long copyWithLimit(InputStream inputStream, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream outputStream = Files.newOutputStream(target)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (properties.getMaxSize() > 0 && total > properties.getMaxSize()) {
                    throw new IllegalArgumentException("file size exceeds maxSize: " + properties.getMaxSize());
                }
                outputStream.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String contentType(Path target, String filename) throws IOException {
        String contentType = Files.probeContentType(target);
        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromName(filename);
        }
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private String publicUrl(String key) {
        String prefix = properties.getPublicUrlPrefix() == null ? "" : properties.getPublicUrlPrefix();
        if (prefix.endsWith("/")) {
            return prefix + key;
        }
        return prefix + "/" + key;
    }
}
