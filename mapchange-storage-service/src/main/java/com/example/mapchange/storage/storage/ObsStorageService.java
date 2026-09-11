package com.example.mapchange.storage.storage;

import com.example.mapchange.common.core.dto.StorageUsage;
import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 华为云 OBS 实现（storage.provider=obs，FR-4.1）。
 * endpoint 为完整 URL（约束 #10）；签名 URL 走 OBS 临时签名。
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "obs")
public class ObsStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObsStorageService.class);

    private final ObsClient client;
    private final String bucket;

    public ObsStorageService(
            @Value("${storage.obs.endpoint}") String endpoint,
            @Value("${storage.obs.access-key:}") String accessKey,
            @Value("${storage.obs.secret-key:}") String secretKey,
            @Value("${storage.obs.bucket}") String bucket) {
        this.client = new ObsClient(accessKey, secretKey, endpoint);
        this.bucket = bucket;
        log.info("[storage] OBS initialized, bucket={}", bucket);
    }

    @Override
    public void put(String key, InputStream in, long size, String contentType) {
        ObjectMetadata meta = new ObjectMetadata();
        if (size >= 0) {
            meta.setContentLength(size);
        }
        if (contentType != null) {
            meta.setContentType(contentType);
        }
        client.putObject(bucket, normalize(key), in, meta);
    }

    @Override
    public InputStream get(String key) {
        return client.getObject(bucket, normalize(key)).getObjectContent();
    }

    @Override
    public String signUrl(String key, Duration ttl) {
        TemporarySignatureRequest req = new TemporarySignatureRequest(
                HttpMethodEnum.GET, ttl.toSeconds());
        req.setBucketName(bucket);
        req.setObjectKey(normalize(key));
        TemporarySignatureResponse resp = client.createTemporarySignature(req);
        return resp.getSignedUrl();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(bucket, normalize(key));
    }

    @Override
    public StorageUsage usage() {
        // 云侧 OBS 容量近似无限：ratio 恒 0（空间保护主要靠定期清理 FR-4.3）
        try {
            long used = client.getBucketStorageInfo(bucket).getObjectNumber() >= 0
                    ? client.getBucketStorageInfo(bucket).getSize() : 0;
            return new StorageUsage(used, -1, 0);
        } catch (Exception e) {
            return new StorageUsage(0, -1, 0);
        }
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }

    private String normalize(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
