package com.example.mapchange.analysis.inference;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.analysis.client.PythonInferSyncClient;
import com.example.mapchange.analysis.client.RemotePlatformClient;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.ModelInfo;
import com.example.mapchange.common.core.dto.python.RemoteInferRequest;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 推理调用路由（FR-5.2/5.3，设计 §3.2）：
 * REMOTE → RemotePlatformClient（Resilience4j：retry + circuitBreaker），请求/响应按
 * 《推理服务联调接口规范》显式映射（与本地 Python 契约解耦）；
 * LOCAL_GPU/LOCAL_CPU → Python infer-service，X-Inference-Provider 请求头携带小写连字符
 * provider 提示（J-01/A-1，映射由 Java 做），同步/异步通道超时分档（J-09/B-2）；
 * 熔断打开且 fallback_to_local=true → LocalFallbackHandler 降级本地（degraded=true）。
 * 返回 FallbackResult 携带降级标记（FR-3.3）。
 */
@Component
public class InferenceRouter {

    private static final Logger log = LoggerFactory.getLogger(InferenceRouter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CB_NAME = "remoteInference";
    private static final String RETRY_NAME = "remoteInference";

    private final InferenceProviderResolver resolver;
    private final PythonInferClient inferClient;
    private final PythonInferSyncClient inferSyncClient;
    private final RemotePlatformClient remoteClient;
    private final LocalFallbackHandler fallbackHandler;
    private final RemoteConfigCache configCache;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final InferenceMetrics metrics;

    public InferenceRouter(InferenceProviderResolver resolver, PythonInferClient inferClient,
                           PythonInferSyncClient inferSyncClient, RemotePlatformClient remoteClient,
                           LocalFallbackHandler fallbackHandler, RemoteConfigCache configCache,
                           CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry,
                           InferenceMetrics metrics) {
        this.resolver = resolver;
        this.inferClient = inferClient;
        this.inferSyncClient = inferSyncClient;
        this.remoteClient = remoteClient;
        this.fallbackHandler = fallbackHandler;
        this.configCache = configCache;
        this.circuitBreaker = cbRegistry.circuitBreaker(CB_NAME);
        this.retry = retryRegistry.retry(RETRY_NAME);
        this.metrics = metrics;
    }

    /** Java 枚举 → Python 小写连字符口径（J-01/A-1；REMOTE 不会走到本地调用） */
    public static String toPythonProviderHint(InferenceProvider provider) {
        return switch (provider) {
            case LOCAL_GPU -> "local-gpu";
            case LOCAL_CPU -> "local-cpu";
            case REMOTE -> "remote";
        };
    }

    /** Python 小写连字符 actual_provider → 对外口径（local_cpu；A-1 回映射） */
    public static String fromPythonProvider(String actualProvider) {
        return actualProvider == null ? null : actualProvider.replace('-', '_');
    }

    public FallbackResult<SegmentationResponse> segment(SegmentationRequest req, boolean sync) {
        InferenceProvider provider = resolver.current();
        long start = System.nanoTime();
        if (provider == InferenceProvider.REMOTE) {
            return withRemote("segment", req, () -> {
                RemoteInferRequest remoteReq = new RemoteInferRequest(remoteModelName(),
                        remoteModelVersion(), "segmentation", toRemoteSegmentInputs(req), null);
                var resp = remoteClient.infer(remoteReq);
                return fromRemoteSegmentOutputs(resp.outputs());
            });
        }
        var result = FallbackResult.direct(sync
                ? inferSyncClient.segment(req, toPythonProviderHint(provider))
                : inferClient.segment(req, toPythonProviderHint(provider)));
        metrics.timer(provider).record(java.time.Duration.ofNanos(System.nanoTime() - start));
        return result;
    }

    public FallbackResult<ChangeMaskResponse> detectChange(ChangeDetectRequest req) {
        InferenceProvider provider = resolver.current();
        if (provider == InferenceProvider.REMOTE) {
            return withRemote("detectChange", req, () -> {
                RemoteInferRequest remoteReq = new RemoteInferRequest(remoteModelName(),
                        remoteModelVersion(), "change_detection", toRemoteChangeInputs(req), null);
                var resp = remoteClient.infer(remoteReq);
                return fromRemoteChangeOutputs(resp.outputs());
            });
        }
        // 变化检测恒为异步链路（AnalysisController 不分流同步），走长超时通道
        return FallbackResult.direct(inferClient.detectChange(req, toPythonProviderHint(provider)));
    }

    // ================= REMOTE 契约映射（《推理服务联调接口规范》§3/§4，与本地 Python 契约解耦） =================

    /** 规范 §3.2：inputs = {image_url, elements, element_class_map, min_area, geo_extent} */
    private JsonNode toRemoteSegmentInputs(SegmentationRequest req) {
        ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("image_url", req.imageUrl());
        inputs.putPOJO("elements", req.elements());
        inputs.putPOJO("element_class_map", req.classMapping());
        if (req.minArea() != null) {
            inputs.put("min_area", req.minArea());
        }
        inputs.putPOJO("geo_extent", req.geoExtent());
        return inputs;
    }

    /** 规范 §3.3：outputs = {masks.{element}, combined_mask, statistics.{element}} */
    private SegmentationResponse fromRemoteSegmentOutputs(JsonNode outputs) {
        Map<String, String> masks = new LinkedHashMap<>();
        outputs.path("masks").properties().forEach(e -> masks.put(e.getKey(), e.getValue().asText()));
        String combined = outputs.path("combined_mask").isTextual()
                ? outputs.path("combined_mask").asText() : null;
        JsonNode statistics = outputs.path("statistics").isObject() ? outputs.get("statistics") : null;
        return new SegmentationResponse(combined, masks, statistics, null,
                new ModelInfo(remoteModelName(), remoteModelVersion(), "remote", null), "remote", null);
    }

    /** 规范 §4.2：inputs = {before_url, after_url, threshold, min_area, auto_align, geo_extent} */
    private JsonNode toRemoteChangeInputs(ChangeDetectRequest req) {
        ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("before_url", req.beforeUrl());
        inputs.put("after_url", req.afterUrl());
        if (req.threshold() != null) {
            inputs.put("threshold", req.threshold());
        }
        if (req.minArea() != null) {
            inputs.put("min_area", req.minArea());
        }
        if (req.autoAlign() != null) {
            inputs.put("auto_align", req.autoAlign());
        }
        inputs.putPOJO("geo_extent", req.geoExtent());
        return inputs;
    }

    /** 规范 §4.3：outputs = {mask, probmap（可选）, statistics} */
    private ChangeMaskResponse fromRemoteChangeOutputs(JsonNode outputs) {
        String mask = outputs.path("mask").isTextual() ? outputs.path("mask").asText() : null;
        String probmap = outputs.path("probmap").isTextual() ? outputs.path("probmap").asText() : null;
        JsonNode statistics = outputs.path("statistics").isObject() ? outputs.get("statistics") : null;
        return new ChangeMaskResponse(mask, probmap, statistics, null, null, null,
                new ModelInfo(remoteModelName(), remoteModelVersion(), "remote", null), "remote", null);
    }

    private String remoteModelName() {
        return configCache.getOrDefault("inference.remote.model-name", "");
    }

    private String remoteModelVersion() {
        return configCache.getOrDefault("inference.remote.model-version", "");
    }

    /** REMOTE 路径：retry → circuitBreaker → 失败/熔断时按配置降级本地 */
    private <T> FallbackResult<T> withRemote(String op, Object req, Supplier<T> remoteCall) {
        Supplier<T> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, remoteCall));
        try {
            return FallbackResult.direct(decorated.get());
        } catch (CallNotPermittedException e) {
            log.warn("远程推理熔断打开（{}）", op);
            return fallback(req, op, e);
        } catch (Exception e) {
            log.warn("远程推理失败（{}）: {}", op, e.getMessage());
            return fallback(req, op, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> FallbackResult<T> fallback(Object req, String op, Exception cause) {
        if (!fallbackHandler.fallbackAllowed()) {
            if (cause instanceof CallNotPermittedException) {
                throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE,
                        "推理服务熔断中且未允许降级（FR-5.3）");
            }
            if (cause instanceof BizException be) {
                throw be;
            }
            throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE, "推理服务不可用: " + cause.getMessage());
        }
        Object resp;
        metrics.countDegraded();   // 实际发生降级才计数（FR-3.3/8.4）
        if (req instanceof SegmentationRequest seg) {
            resp = fallbackHandler.segment(seg);
        } else if (req instanceof ChangeDetectRequest cd) {
            resp = fallbackHandler.detectChange(cd);
        } else {
            throw FallbackResult.unavailable("未知请求类型: " + op);
        }
        return (FallbackResult<T>) FallbackResult.degraded(resp);
    }
}
