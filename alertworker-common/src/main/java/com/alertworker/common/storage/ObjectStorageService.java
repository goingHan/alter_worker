package com.alertworker.common.storage;

/**
 * Adapter over a pluggable object store.
 *
 * <p>The upload location and the public presentation URL are intentionally decoupled: {@link #put} writes under the
 * configured endpoint/bucket/key, while {@link #publicUrl} builds the URL from an independently configured public
 * base (which may include the bucket name and a path prefix). Implementations are selected by
 * {@code alertworker.storage.type}.
 */
public interface ObjectStorageService {

    /**
     * Uploads {@code content} under {@code objectKey}.
     *
     * @return the stored object key (same value passed in)
     */
    String put(String objectKey, byte[] content, String contentType);

    /** Resolves the public presentation URL for an object key. */
    String publicUrl(String objectKey);

    /** True if an object exists at {@code objectKey}. */
    boolean exists(String objectKey);

    /** Deletes the object at {@code objectKey} (no-op if missing). */
    void delete(String objectKey);
}
