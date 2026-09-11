package com.example.mapchange.config.service;

import com.example.mapchange.common.core.dto.CapabilitiesView;
import com.example.mapchange.config.client.AnalysisClient;
import com.example.mapchange.config.client.StorageClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * capabilities 聚合（FR-10.5/10.6/10.7）：本地功能开关 + analysis-service provider 状态 +
 * storage-service 存储提供方 + 鉴权开关状态（auth.enabled）+ 瓦片矩阵参数 + 默认参数集。
 * 级联可用性（评审 P-06）：单边 Feign 失败时对应分组降级为 provider="unknown" 且响应带
 * partial=true，不整体 5xx；聚合结果短时缓存 30s；单边调用超时由 Feign 客户端配置控制（2s）。
 */
@Service
public class CapabilityService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_SECONDS = 30;

    private final ConfigService configService;
    private final AnalysisClient analysisClient;
    private final StorageClient storageClient;
    private final boolean authEnabled;

    private volatile CapabilitiesView cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public CapabilityService(ConfigService configService, AnalysisClient analysisClient,
                             StorageClient storageClient,
                             @Value("${security.auth.enabled:true}") boolean authEnabled) {
        this.configService = configService;
        this.analysisClient = analysisClient;
        this.storageClient = storageClient;
        this.authEnabled = authEnabled;
    }

    public CapabilitiesView aggregate() {
        if (cached != null && cachedAt.plusSeconds(CACHE_TTL_SECONDS).isAfter(Instant.now())) {
            return cached;
        }
        boolean partial = false;

        // 功能开关（features 组，L3）
        Map<String, Boolean> features = new LinkedHashMap<>();
        configService.groupValues("features").forEach((k, v) ->
                features.put(k.substring("features.".length()), Boolean.parseBoolean(v)));

        // 推理提供方（单边失败降级 unknown + partial，评审 P-06）
        String inferenceProvider;
        try {
            var resp = analysisClient.status();
            inferenceProvider = resp.data() != null ? resp.data().provider() : "unknown";
        } catch (Exception e) {
            log.warn("capabilities: analysis-service 状态聚合失败，降级 unknown: {}", e.getMessage());
            inferenceProvider = "unknown";
            partial = true;
        }

        // 存储提供方
        String storageProvider;
        try {
            var resp = storageClient.provider();
            storageProvider = resp.data() != null ? resp.data() : "unknown";
        } catch (Exception e) {
            log.warn("capabilities: storage-service 状态聚合失败，降级 unknown: {}", e.getMessage());
            storageProvider = "unknown";
            partial = true;
        }

        // 瓦片矩阵参数（FR-10.6，前后端同源下发）与默认参数集（feature 组）
        Map<String, String> feature = configService.groupValues("feature");
        ObjectNode tileMatrix = JsonNodeFactory.instance.objectNode();
        tileMatrix.put("origin", readJson(feature.get("feature.tile-matrix.origin")));
        tileMatrix.put("span_base", Double.parseDouble(feature.get("feature.tile-matrix.span-base")));
        tileMatrix.put("start_level", Integer.parseInt(feature.get("feature.tile-matrix.start-level")));

        ObjectNode defaults = JsonNodeFactory.instance.objectNode();
        defaults.put("threshold", Double.parseDouble(feature.get("feature.defaults.threshold")));
        defaults.put("min_area", Integer.parseInt(feature.get("feature.defaults.min-area")));
        ObjectNode diffColors = JsonNodeFactory.instance.objectNode();
        diffColors.put("added", feature.get("feature.diff-colors.added"));
        diffColors.put("removed", feature.get("feature.diff-colors.removed"));
        defaults.set("diff_colors", diffColors);

        CapabilitiesView view = new CapabilitiesView(features, inferenceProvider, storageProvider,
                authEnabled, tileMatrix, defaults, partial);
        cached = view;
        cachedAt = Instant.now();
        return view;
    }

    private JsonNode readJson(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return JsonNodeFactory.instance.arrayNode();
        }
    }
}
