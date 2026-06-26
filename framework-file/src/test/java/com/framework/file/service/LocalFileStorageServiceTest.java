package com.framework.file.service;

import com.framework.file.config.FileProperties;
import com.framework.file.model.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesLoadsAndDeletesFilesUnderConfiguredBasePath() throws Exception {
        FileProperties properties = new FileProperties();
        properties.setBasePath(tempDir.toString());
        properties.setPublicUrlPrefix("/public");
        LocalFileStorageService storageService = new LocalFileStorageService(properties);

        StoredFile storedFile = storageService.store(
                "invoice ../2026.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(storedFile.getOriginalFilename()).isEqualTo("2026.txt");
        assertThat(storedFile.getKey()).doesNotContain("/", " ");
        assertThat(storedFile.getUrl()).isEqualTo("/public/" + storedFile.getKey());
        assertThat(storedFile.getContentType()).isEqualTo("text/plain");
        assertThat(Files.exists(tempDir.resolve(storedFile.getKey()))).isTrue();
        assertThat(new String(storageService.load(storedFile.getKey()).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("hello");

        storageService.delete(storedFile.getKey());

        assertThat(Files.exists(tempDir.resolve(storedFile.getKey()))).isFalse();
    }

    @Test
    void rejectsFilesLargerThanConfiguredLimitAndCleansPartialFile() throws Exception {
        FileProperties properties = properties();
        properties.setMaxSize(4);
        LocalFileStorageService storageService = new LocalFileStorageService(properties);

        assertThatThrownBy(() -> storageService.store(
                        "large.txt",
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file size exceeds maxSize");

        assertThat(Files.list(tempDir).toList()).isEmpty();
    }

    @Test
    void rejectsDisallowedExtensionsCaseInsensitively() {
        FileProperties properties = properties();
        properties.setAllowedExtensions(List.of("jpg", "png"));
        LocalFileStorageService storageService = new LocalFileStorageService(properties);

        assertThatThrownBy(() -> storageService.store(
                        "payload.TXT",
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file extension is not allowed");
    }

    @Test
    void rejectsNullInputStreamWithClearMessage() {
        LocalFileStorageService storageService = new LocalFileStorageService(properties());

        assertThatThrownBy(() -> storageService.store("invoice.txt", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputStream must not be null");
    }

    @Test
    void fallsBackToSafeFilenameWhenOriginalNameHasNoFileName() throws Exception {
        LocalFileStorageService storageService = new LocalFileStorageService(properties());

        StoredFile storedFile = storageService.store(
                "/",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(storedFile.getOriginalFilename()).isEqualTo("file");
        assertThat(storedFile.getKey()).endsWith("-file");
        assertThat(Files.exists(tempDir.resolve(storedFile.getKey()))).isTrue();
    }

    @Test
    void rejectsUnsafeStorageConfiguration() {
        FileProperties blankBasePath = properties();
        blankBasePath.setBasePath(" ");
        FileProperties negativeMaxSize = properties();
        negativeMaxSize.setMaxSize(-1);
        FileProperties zeroMaxSize = properties();
        zeroMaxSize.setMaxSize(0);
        FileProperties blankAllowedExtension = properties();
        blankAllowedExtension.setAllowedExtensions(List.of("jpg", " "));

        assertThatThrownBy(() -> new LocalFileStorageService(blankBasePath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base-path");
        assertThatThrownBy(() -> new LocalFileStorageService(negativeMaxSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-size");
        assertThatThrownBy(() -> new LocalFileStorageService(zeroMaxSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-size");
        assertThatThrownBy(() -> new LocalFileStorageService(blankAllowedExtension))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-extensions");
    }

    @Test
    void rejectsUnsafeKeysInsteadOfSanitizingThemForLookup() {
        LocalFileStorageService storageService = new LocalFileStorageService(properties());

        assertThatThrownBy(() -> storageService.load("../invoice.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file key is not allowed");
        assertThatThrownBy(() -> storageService.delete("invoice 2026.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file key is not allowed");
        assertThatThrownBy(() -> storageService.load(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file key must not be blank");
    }

    @Test
    void joinsPublicUrlWithoutDoubleSlash() throws Exception {
        FileProperties properties = properties();
        properties.setPublicUrlPrefix("/public/");
        LocalFileStorageService storageService = new LocalFileStorageService(properties);

        StoredFile storedFile = storageService.store(
                "invoice.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(storedFile.getUrl()).isEqualTo("/public/" + storedFile.getKey());
    }

    private FileProperties properties() {
        FileProperties properties = new FileProperties();
        properties.setBasePath(tempDir.toString());
        properties.setPublicUrlPrefix("/public");
        return properties;
    }
}
