package com.example.mapchange.storage.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.BucketStat;
import com.aliyun.oss.model.ObjectMetadata;
import com.example.mapchange.common.core.dto.StorageUsage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/**
 * 阿里云 OSS 实现（storage.provider=oss，FR-4.1）。
 * endpoint 为完整 URL（约束 #10）；签名 URL 走 OSS 原生 presign。
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "oss")
public class OssStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OSS client;
    private final String bucket;

    public OssStorageService(
            @Value("${storage.oss.endpoint}") String endpoint,
            @Value("${storage.oss.access-key:}") String accessKey,
            @Value("${storage.oss.secret-key:}") String secretKey,
            @Value("${storage.oss.bucket}") String bucket) {
        this.client = new OSSClientBuilder().build(endpoint, accessKey, secretKey);
        this.bucket = bucket;
        log.info("[storage] OSS initialized, bucket={}", bucket);
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
        Date expiration = new Date(System.currentTimeMillis() + ttl.toMillis());
        return client.generatePresignedUrl(bucket, normalize(key), expiration).toString();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(bucket, normalize(key));
    }

    @Override
    public StorageUsage usage() {
        // 云侧 OSS 容量近似无限：返回已用量，ratio 恒 0（空间保护主要靠定期清理 FR-4.3）
        BucketStat stat = client.getBucketStat(bucket);
        return new StorageUsage(stat.getStorageSize(), -1, 0);
    }

    @PreDestroy
    public void close() {
        client.shutdown();
    }

    private String normalize(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
