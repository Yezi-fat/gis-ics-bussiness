package com.example.mapchange.analysis.inference;

import com.example.mapchange.common.core.dto.python.InferHealthResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** 三级解析分支测试（J-022：配/未配 remote × 有/无 GPU 四种组合；GPU 判定以 gpu_usable 为准，B-3） */
class InferenceProviderResolverTest {

    private RemoteConfigCache configCache;
    private EnvironmentProbe probe;
    private InferenceProviderResolver resolver;

    private static final InferHealthResponse GPU_HEALTH = new InferHealthResponse("ok",
            true, true, 8192L, 8,
            new InferHealthResponse.InferModelsHealth(
                    new InferHealthResponse.ModelHealth(true, "v1", "local-gpu", List.of(0, 1, 2, 3, 4)),
                    null), false);
    private static final InferHealthResponse CPU_HEALTH = new InferHealthResponse("ok",
            false, false, null, 8,
            new InferHealthResponse.InferModelsHealth(
                    new InferHealthResponse.ModelHealth(true, "v1", "local-cpu", List.of(0, 1, 2, 3, 4)),
                    null), false);

    @BeforeEach
    void setUp() {
        configCache = Mockito.mock(RemoteConfigCache.class);
        probe = Mockito.mock(EnvironmentProbe.class);
        resolver = new InferenceProviderResolver(configCache, probe);
    }

    private void mockConfig(String provider, String endpoint) {
        when(configCache.getOrDefault("inference.provider", "auto")).thenReturn(provider);
        when(configCache.getOrDefault("inference.remote.endpoint", "")).thenReturn(endpoint);
    }

    @Test
    void autoWithReachableRemoteResolvesRemote() {
        mockConfig("auto", "http://infer-platform:8000/api/v2/inference");
        when(probe.checkRemote("http://infer-platform:8000/api/v2/inference")).thenReturn(true);
        resolver.reResolve();
        assertEquals(InferenceProvider.REMOTE, resolver.current());
    }

    @Test
    void autoWithRemoteConfiguredButUnreachableFallsToGpu() {
        mockConfig("auto", "http://infer-platform:8000/api/v2/inference");
        when(probe.checkRemote(Mockito.anyString())).thenReturn(false);
        when(probe.probeLocal()).thenReturn(GPU_HEALTH);
        resolver.reResolve();
        assertEquals(InferenceProvider.LOCAL_GPU, resolver.current());
    }

    @Test
    void autoWithoutRemoteAndGpuResolvesCpu() {
        mockConfig("auto", "");   // 未配置 remote：正常形态
        when(probe.probeLocal()).thenReturn(CPU_HEALTH);
        resolver.reResolve();
        assertEquals(InferenceProvider.LOCAL_CPU, resolver.current());
    }

    @Test
    void autoWithoutRemoteAndInferDownResolvesCpu() {
        mockConfig("auto", "");
        when(probe.probeLocal()).thenReturn(null);
        resolver.reResolve();
        assertEquals(InferenceProvider.LOCAL_CPU, resolver.current());
    }

    @Test
    void forcedProviderHonored() {
        mockConfig("local-cpu", "");
        resolver.reResolve();
        assertEquals(InferenceProvider.LOCAL_CPU, resolver.current());

        mockConfig("remote", "http://x/api");
        when(probe.checkRemote(Mockito.anyString())).thenReturn(false);  // 冲突也按配置执行（WARN）
        resolver.reResolve();
        assertEquals(InferenceProvider.REMOTE, resolver.current());
    }

    @Test
    void configServiceDownFallsBackToLocalCpuWithoutBlocking() {
        when(configCache.getOrDefault(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new RuntimeException("config-service 未就绪"));
        resolver.reResolve();   // 不抛异常
        assertEquals(InferenceProvider.LOCAL_CPU, resolver.current());
    }
}
