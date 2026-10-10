package com.alertworker.processor.storage;

import com.alertworker.common.storage.ObjectStorageService;
import com.alertworker.common.storage.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Development / offline adapter that writes objects under a local directory. Selected when
 * {@code alertworker.storage.type=local} or unset. The public URL is the {@code file:} URI of the stored file
 * (or the configured public base when present).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alertworker.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements ObjectStorageService {

    private final StorageProperties properties;
    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create local storage root: " + root, e);
        }
        log.info("Local object storage initialized at {}", root);
    }

    @Override
    public String put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write local object: " + objectKey, e);
        }
        return objectKey;
    }

    @Override
    public String publicUrl(String objectKey) {
        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            return properties.publicUrl(objectKey);
        }
        return resolve(objectKey).toUri().toString();
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete local object: " + objectKey, e);
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Object key escapes the storage root: " + objectKey);
        }
        return target;
    }
}
