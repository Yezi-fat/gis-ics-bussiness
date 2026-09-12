package com.example.mapchange.analysis.inference;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.analysis.client.PythonInferSyncClient;
import com.example.mapchange.analysis.client.RemotePlatformClient;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.ModelInfo;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 熔断降级测试（J-025：远程故障 → 熔断打开 → 自动降级 → degraded=true；不允许降级 → INFERENCE_UNAVAILABLE）；
 *  同步/异步通道选择与 provider 提示头映射（J-01/A-1、J-09/B-2） */
class InferenceRouterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SegmentationResponse LOCAL_RESP = new SegmentationResponse(
            null, Map.of(), null, new double[]{0, 1, 0, 0, 0, -1},
            new ModelInfo("seg", "v1", "local-cpu", List.of(0, 1)), "local-cpu", 1L);

    private InferenceProviderResolver resolver;
    private PythonInferClient inferClient;
    private PythonInferSyncClient inferSyncClient;
    private RemotePlatformClient remoteClient;
    private LocalFallbackHandler fallbackHandler;
    private RemoteConfigCache configCache;
    private CircuitBreakerRegistry cbRegistry;
    private InferenceRouter router;

    private static final SegmentationRequest REQ = new SegmentationRequest(
            "http://x/img.png", new double[]{104.0, 30.6, 104.1, 30.7}, List.of("forest"),
            Map.of("forest", 1), Map.of("forest", "#228B22"), null, "seg", "v1");

    @BeforeEach
    void setUp() {
        resolver = Mockito.mock(InferenceProviderResolver.class);
        inferClient = Mockito.mock(PythonInferClient.class);
        inferSyncClient = Mockito.mock(PythonInferSyncClient.class);
        remoteClient = Mockito.mock(RemotePlatformClient.class);
        configCache = Mockito.mock(RemoteConfigCache.class);
        fallbackHandler = new LocalFallbackHandler(inferClient, configCache);
        cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)).build());
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2).waitDuration(Duration.ofMillis(1)).build());
        router = new InferenceRouter(resolver, inferClient, inferSyncClient, remoteClient,
                fallbackHandler, configCache, cbRegistry, retryRegistry,
                new InferenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void localProviderCallsPythonDirectlyWithProviderHint() {
        when(resolver.current()).thenReturn(InferenceProvider.LOCAL_CPU);
        when(inferClient.segment(any(), eq("local-cpu"))).thenReturn(LOCAL_RESP);
        var result = router.segment(REQ, false);
        assertEquals(LOCAL_RESP, result.response());
        org.junit.jupiter.api.Assertions.assertFalse(result.degraded());
    }

    @Test
    void syncChannelUsesSyncClient() {
        when(resolver.current()).thenReturn(InferenceProvider.LOCAL_GPU);
        when(inferSyncClient.segment(any(), eq("local-gpu"))).thenReturn(LOCAL_RESP);
        var result = router.segment(REQ, true);
        assertEquals(LOCAL_RESP, result.response());
        Mockito.verify(inferSyncClient).segment(any(), eq("local-gpu"));
        Mockito.verifyNoInteractions(inferClient);
    }

    @Test
    void remoteFailureFallsBackToLocalWithDegraded() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("true");
        when(configCache.getOrDefault("inference.remote.model-name", "")).thenReturn("seg");
        when(configCache.getOrDefault("inference.remote.model-version", "")).thenReturn("v1");
        when(remoteClient.infer(any())).thenThrow(
                new BizException(ErrorCode.INFERENCE_TIMEOUT, "timeout"));
        when(inferClient.segmentWithDefaultProvider(any())).thenReturn(LOCAL_RESP);

        var result = router.segment(REQ, false);
        assertTrue(result.degraded());
        assertEquals(LOCAL_RESP, result.response());
    }

    @Test
    void circuitOpensAfterFailuresThenFallback() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("true");
        when(configCache.getOrDefault("inference.remote.model-name", "")).thenReturn("seg");
        when(configCache.getOrDefault("inference.remote.model-version", "")).thenReturn("v1");
        when(remoteClient.infer(any())).thenThrow(new RuntimeException("boom"));
        when(inferClient.segmentWithDefaultProvider(any())).thenReturn(LOCAL_RESP);

        router.segment(REQ, false);   // 失败 1
        router.segment(REQ, false);   // 失败 2 → 熔断打开
        assertEquals(CircuitBreaker.State.OPEN,
                cbRegistry.circuitBreaker("remoteInference").getState());
        var result = router.segment(REQ, false);   // 熔断中 → CallNotPermitted → 降级
        assertTrue(result.degraded());
    }

    @Test
    void noFallbackConfiguredThrowsInferenceUnavailable() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.fallback-to-local", "true")).thenReturn("false");
        when(remoteClient.infer(any())).thenThrow(new RuntimeException("boom"));
        BizException e = assertThrows(BizException.class, () -> router.segment(REQ, false));
        assertEquals(ErrorCode.INFERENCE_UNAVAILABLE, e.errorCode());
    }

    @Test
    void remoteSuccessPassesThroughWithSpecMapping() {
        when(resolver.current()).thenReturn(InferenceProvider.REMOTE);
        when(configCache.getOrDefault("inference.remote.model-name", "")).thenReturn("seg");
        when(configCache.getOrDefault("inference.remote.model-version", "")).thenReturn("v9");
        // 联调规范 §3.3 outputs 形态：masks.{element} / combined_mask / statistics
        var outputs = MAPPER.valueToTree(java.util.Map.of(
                "masks", java.util.Map.of("forest", "b64mask"),
                "combined_mask", "b64combined",
                "statistics", java.util.Map.of("forest", java.util.Map.of("area_px", 1))));
        when(remoteClient.infer(any())).thenReturn(new RemoteInferResponse("r1", outputs));
        var result = router.segment(REQ, false);
        org.junit.jupiter.api.Assertions.assertFalse(result.degraded());
        assertEquals("b64mask", result.response().perClassMasks().get("forest"));
        assertEquals("b64combined", result.response().combinedMaskPngB64());
        assertEquals("v9", result.response().modelInfo().version());
        assertEquals("remote", result.response().actualProvider());
    }

    @Test
    void providerHintMapping() {
        assertEquals("local-gpu", InferenceRouter.toPythonProviderHint(InferenceProvider.LOCAL_GPU));
        assertEquals("local-cpu", InferenceRouter.toPythonProviderHint(InferenceProvider.LOCAL_CPU));
        assertEquals("remote", InferenceRouter.toPythonProviderHint(InferenceProvider.REMOTE));
        assertEquals("local_cpu", InferenceRouter.fromPythonProvider("local-cpu"));
        assertEquals("remote", InferenceRouter.fromPythonProvider("remote"));
        org.junit.jupiter.api.Assertions.assertNull(InferenceRouter.fromPythonProvider(null));
    }
}
