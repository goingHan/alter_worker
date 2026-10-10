package com.alertworker.common.storage;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Object-storage configuration shared by the storage adapters.
 *
 * <p>Example (Aliyun OSS behind a custom domain that already points at the bucket root, so the public URL does NOT
 * repeat the bucket name):
 * <pre>
 * alertworker:
 *   storage:
 *     type: oss
 *     public-base-url: https://chatfiles.tydiczt.com
 *     bucket: nbchat-test
 *     home: tmp_ops_reports
 *     oss:
 *       endpoint: oss-cn-beijing.aliyuncs.com
 *       region: cn-beijing
 *       access-key-id: ...
 *       access-key-secret: ...
 * </pre>
 * Object key (bucket-relative, used for the OSS upload) = {@code home + "/" + relative};
 * public URL = {@code public-base-url + "/" + objectKey} — the bucket is only used for the upload, never in the URL.
 */
@Data
@Component
@Slf4j
@ConfigurationProperties(prefix = "alertworker.storage")
public class StorageProperties {

    /** Adapter selector: {@code oss} | {@code local}. */
    private String type = "local";

    /** Public presentation base (custom domain pointing at the bucket root; may include a path prefix). */
    private String publicBaseUrl;

    /** Bucket name for the OSS adapter (used only for the upload, not in the public URL). */
    private String bucket;

    /** Object key prefix inside the bucket, e.g. {@code tmp_digital}. */
    private String home;

    /** Local adapter root directory (dev fallback). */
    private String localRoot = "alertworker-storage";

    private Oss oss = new Oss();

    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: log the effective object-storage config once at startup so a
    // shadowing source (env var / local default) is immediately visible when diagnosing 404 hosted-report links.
    @PostConstruct
    public void logEffectiveConfig() {
        log.info("Effective object storage config: type={} bucket={} home={} publicBaseUrl={}",
                type, bucket, home, publicBaseUrl);
        // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: a blank public-base-url makes publicUrl() return a relative
        // path, which DingDing rejects (hosted report links fail). Surface it loudly instead of shipping bad links.
        if ("oss".equalsIgnoreCase(type) && (publicBaseUrl == null || publicBaseUrl.isBlank())) {
            log.warn("alertworker.storage.public-base-url is blank while type=oss; hosted report links will be "
                    + "relative/invalid and cannot be sent to DingDing. Configure it (e.g. https://your-domain).");
        }
    }

    /** Composes the bucket-relative object key: {@code home + "/" + relative}. */
    public String objectKey(String relative) {
        String h = trimSlashes(home);
        String r = trimLeading(relative);
        if (h.isEmpty()) {
            return r;
        }
        return r.isEmpty() ? h : h + "/" + r;
    }

    /**
     * Public presentation URL: {@code public-base-url + "/" + objectKey}. The bucket is intentionally NOT part of the
     * URL — the custom domain already points at the bucket root (see the class javadoc example).
     */
    public String publicUrl(String objectKey) {
        StringBuilder sb = new StringBuilder(trimTrailing(publicBaseUrl));
        String k = trimLeading(objectKey);
        if (!k.isEmpty()) {
            sb.append('/').append(k);
        }
        return sb.toString();
    }

    private static String trimLeading(String value) {
        return value == null ? "" : value.replaceAll("^/+", "");
    }

    private static String trimTrailing(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static String trimSlashes(String value) {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }

    @Data
    public static class Oss {
        private String endpoint;
        private String region;
        private String accessKeyId;
        private String accessKeySecret;
    }
}
