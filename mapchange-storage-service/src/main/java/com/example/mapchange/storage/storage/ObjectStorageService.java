package com.example.mapchange.storage.storage;

import com.example.mapchange.common.core.dto.StorageUsage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 对象存储抽象（FR-4.1/4.4，设计 §2.8/§3.4）：业务代码只依赖本接口。
 * 三实现按 storage.provider=oss | obs | local（auto 探测，见 StorageProviderEnvironmentPostProcessor）装配。
 */
public interface ObjectStorageService {

    void put(String key, InputStream in, long size, String contentType);

    InputStream get(String key);

    /** oss/obs 原生 presign；local 返回下载地址 + HMAC 时效 token（前端可达 base） */
    String signUrl(String key, Duration ttl);

    /**
     * purpose=INTERNAL 的签名 URL（评审 P-03：供 Python 直连拉取影像，短 TTL）。
     * 默认与 signUrl 相同；local 实现覆写为内网 base。
     */
    default String signUrlInternal(String key, Duration ttl) {
        return signUrl(key, ttl);
    }

    void delete(String key);

    StorageUsage usage();
}
