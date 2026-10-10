package com.alertworker.processor.storage;

import com.alertworker.common.storage.ObjectStorageService;
import com.alertworker.common.storage.StorageProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Aliyun OSS adapter. Uploads through the configured endpoint + bucket; the public URL is composed by
 * {@link StorageProperties#publicUrl(String)} (public-base-url + objectKey — the bucket is not repeated in the URL,
 * since the custom domain already points at the bucket root).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alertworker.storage", name = "type", havingValue = "oss")
public class AliyunOssStorage implements ObjectStorageService {

    private final StorageProperties properties;
    private volatile OSS client;

    private OSS client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Oss oss = properties.getOss();
                    if (oss.getEndpoint() == null || properties.getBucket() == null
                            || oss.getAccessKeyId() == null || oss.getAccessKeySecret() == null) {
                        throw new IllegalStateException(
                                "Aliyun OSS storage selected but alertworker.storage.oss.* is incomplete");
                    }
                    client = new OSSClientBuilder()
                            .build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
                    log.info("Initialized Aliyun OSS client: endpoint={} bucket={} publicBaseUrl={} home={}",
                            oss.getEndpoint(), properties.getBucket(), properties.getPublicBaseUrl(),
                            properties.getHome());
                }
            }
        }
        return client;
    }

    @Override
    public String put(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        client().putObject(properties.getBucket(), objectKey, new ByteArrayInputStream(content), metadata);
        return objectKey;
    }

    @Override
    public String publicUrl(String objectKey) {
        return properties.publicUrl(objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return client().doesObjectExist(properties.getBucket(), objectKey);
    }

    @Override
    public void delete(String objectKey) {
        client().deleteObject(properties.getBucket(), objectKey);
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
