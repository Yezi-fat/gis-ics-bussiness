package com.example.mapchange.result.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ExportResponse;
import com.example.mapchange.common.core.dto.FeatureCollection;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.result.client.StorageClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * GeoJSON 导出（FR-4.2，J-031）：返回签名 URL（或 local 模式文件下载地址）。
 * 开关 export.geojson.enabled=false 时拒绝（L3 功能开关，FR-10.5）。
 */
@Service
public class ExportService {

    private final LazyVectorizeService lazyVectorizeService;
    private final StorageClient storageClient;
    private final RemoteConfigCache configCache;

    public ExportService(LazyVectorizeService lazyVectorizeService, StorageClient storageClient,
                         RemoteConfigCache configCache) {
        this.lazyVectorizeService = lazyVectorizeService;
        this.storageClient = storageClient;
        this.configCache = configCache;
    }

    public ExportResponse exportGeoJson(UUID taskId) {
        if (!Boolean.parseBoolean(configCache.getOrDefault("features.export-geojson.enabled", "true"))) {
            throw new BizException(ErrorCode.FORBIDDEN, "GeoJSON 导出功能已关闭（export.geojson.enabled=false）");
        }
        String key = lazyVectorizeService.ensureVectorized(taskId);   // 返回 regions.geojson 的 key
        Duration ttl = Duration.parse(configCache.getOrDefault("storage.sign-url-ttl", "PT2H"));
        String url = storageClient.signUrl(new SignUrlRequest(key, "FRONTEND", null)).data().url();
        return new ExportResponse(url, Instant.now().plus(ttl).toString());
    }
}
