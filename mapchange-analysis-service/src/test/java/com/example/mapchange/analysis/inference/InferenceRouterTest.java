package com.example.mapchange.analysis.inference;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.analysis.client.RemotePlatformClient;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.RemoteInferResponse;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 熔断降级测试（J-025：远程故障 → 熔断打开 → 自动降级 → degraded=true；不允许降级 → INFERENCE_UNAVAILABLE） */
class InferenceRouterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SegmentationResponse LOCAL_RESP =
            new SegmentationResponse("t", Map.of(), null, null, "seg", "v1");

    private InferenceProviderResolver resolver;
    private PythonInferClient inferClient;
    private RemotePlatformClient remoteClient;
    private LocalFallbackHandler fallbackHandler;
    private RemoteConfigCache configCache;
    private CircuitBreakerRegistry cbRegistry;
    private InferenceRouter router;

    private static final SegmentationRequest REQ = new SegmentationRequest(
            "t", "http://x/img.png", java.util.List.of("forest"), Map.of("forest", 1),
            null, null, "seg", "v1");

    @BeforeEach
    void setUp() {
        resolver = Mockito.mock(InferenceProviderResolver.class);
        inferClient = Mockito.mock(PythonInferClient.class);
        remoteClient = Mockito.mock(RemotePlatformClient.class);
        configCache = Mockito.mock(RemoteConfigCache.class);
        fallbackHandler = new LocalFallbackHandler(inferClient, configCache);
        cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)).build());
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2).waitDuration(Duration.ofMillis(1)).build());
        router = new InferenceRouter(resolver, inferClient, remoteClient, fallbackHandler,
                cbRegistry, retryRegistry, new InferenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void localProviderCallsPythonDirectly() {
        when(resolver.current()).thenReturn(InferenceProvider.LOCAL_CPU);
        when(inferClient.segment(any())).thenReturn(LOCAL_RESP);
        var result = router.segment(REQ);
        assertEquals(LOCAL_RESP, result.response());
        org.junit.jupiter.api.Assertions.assertFalse(result.degraded());
    }

    @Test
    void remoteFailureFallsBackToLocalWithDegraded() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("true");
        when(remoteClient.infer(any())).thenThrow(
                new BizException(ErrorCode.INFERENCE_TIMEOUT, "timeout"));
        when(inferClient.segment(any())).thenReturn(LOCAL_RESP);

        var result = router.segment(REQ);
        assertTrue(result.degraded());
        assertEquals(LOCAL_RESP, result.response());
    }

    @Test
    void circuitOpensAfterFailuresThenFallback() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("true");
        when(remoteClient.infer(any())).thenThrow(new RuntimeException("boom"));
        when(inferClient.segment(any())).thenReturn(LOCAL_RESP);

        router.segment(REQ);   // 失败 1
        router.segment(REQ);   // 失败 2 → 熔断打开
        assertEquals(CircuitBreaker.State.OPEN,
                cbRegistry.circuitBreaker("remoteInference").getState());
        var result = router.segment(REQ);   // 熔断中 → CallNotPermitted → 降级
        assertTrue(result.degraded());
    }

    @Test
    void noFallbackConfiguredThrowsInferenceUnavailable() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("false");
        when(remoteClient.infer(any())).thenThrow(new RuntimeException("boom"));
        BizException e = assertThrows(BizException.class, () -> router.segment(REQ));
        assertEquals(ErrorCode.INFERENCE_UNAVAILABLE, e.errorCode());
    }

    @Test
    void remoteSuccessPassesThrough() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        var outputs = MAPPER.valueToTree(java.util.Map.of("taskId", "t", "masks", java.util.Map.of(),
                "modelName", "seg", "modelVersion", "v1"));
        when(remoteClient.infer(any())).thenReturn(new RemoteInferResponse("r1", outputs));
        var result = router.segment(REQ);
        org.junit.jupiter.api.Assertions.assertFalse(result.degraded());
        assertEquals("t", result.response().taskId());
    }
}
