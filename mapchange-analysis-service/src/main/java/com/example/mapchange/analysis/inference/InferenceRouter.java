package com.example.mapchange.analysis.inference;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.analysis.client.RemotePlatformClient;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.RemoteInferRequest;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 推理调用路由（FR-5.2/5.3，设计 §3.2）：
 * REMOTE → RemotePlatformClient（Resilience4j：retry + circuitBreaker）；
 * LOCAL_GPU/LOCAL_CPU → Python infer-service（provider 提示由请求携带，见 SegmentationRequest）；
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
    private final RemotePlatformClient remoteClient;
    private final LocalFallbackHandler fallbackHandler;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final InferenceMetrics metrics;

    public InferenceRouter(InferenceProviderResolver resolver, PythonInferClient inferClient,
                           RemotePlatformClient remoteClient, LocalFallbackHandler fallbackHandler,
                           CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry,
                           InferenceMetrics metrics) {
        this.resolver = resolver;
        this.inferClient = inferClient;
        this.remoteClient = remoteClient;
        this.fallbackHandler = fallbackHandler;
        this.circuitBreaker = cbRegistry.circuitBreaker(CB_NAME);
        this.retry = retryRegistry.retry(RETRY_NAME);
        this.metrics = metrics;
    }

    public FallbackResult<SegmentationResponse> segment(SegmentationRequest req) {
        InferenceProvider provider = resolver.current();
        long start = System.nanoTime();
        if (provider == InferenceProvider.REMOTE) {
            return withRemote("segment", req, () -> {
                RemoteInferRequest remoteReq = new RemoteInferRequest(null, null, "segmentation",
                        MAPPER.valueToTree(req), null);
                var resp = remoteClient.infer(remoteReq);
                return MAPPER.convertValue(resp.outputs(), SegmentationResponse.class);
            });
        }
        var result = FallbackResult.direct(inferClient.segment(req));
        metrics.timer(provider).record(java.time.Duration.ofNanos(System.nanoTime() - start));
        return result;
    }

    public FallbackResult<ChangeMaskResponse> detectChange(ChangeDetectRequest req) {
        InferenceProvider provider = resolver.current();
        if (provider == InferenceProvider.REMOTE) {
            return withRemote("detectChange", req, () -> {
                RemoteInferRequest remoteReq = new RemoteInferRequest(null, null, "change_detection",
                        MAPPER.valueToTree(req), null);
                var resp = remoteClient.infer(remoteReq);
                return MAPPER.convertValue(resp.outputs(), ChangeMaskResponse.class);
            });
        }
        return FallbackResult.direct(inferClient.detectChange(req));
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
