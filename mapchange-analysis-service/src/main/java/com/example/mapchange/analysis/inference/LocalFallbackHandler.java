package com.example.mapchange.analysis.inference;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地降级（FR-5.3）：熔断打开且 fallback_to_local=true 时降级本地推理，标记 degraded=true。
 */
@Component
public class LocalFallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(LocalFallbackHandler.class);

    private final PythonInferClient inferClient;
    private final RemoteConfigCache configCache;

    public LocalFallbackHandler(PythonInferClient inferClient, RemoteConfigCache configCache) {
        this.inferClient = inferClient;
        this.configCache = configCache;
    }

    public boolean fallbackAllowed() {
        return Boolean.parseBoolean(configCache.getOrDefault("inference.fallback-to-local", "true"));
    }

    /** 降级到本地执行分割（标记 degraded；不带 provider 提示，由 infer-service 本地默认兜底） */
    public SegmentationResponse segment(SegmentationRequest req) {
        log.warn("远程推理降级到本地执行（默认 provider 兜底）");
        return inferClient.segmentWithDefaultProvider(req);
    }

    /** 降级到本地执行变化检测（标记 degraded；不带 provider 提示） */
    public ChangeMaskResponse detectChange(ChangeDetectRequest req) {
        log.warn("远程推理降级到本地执行（默认 provider 兜底）");
        return inferClient.detectChangeWithDefaultProvider(req);
    }
}
