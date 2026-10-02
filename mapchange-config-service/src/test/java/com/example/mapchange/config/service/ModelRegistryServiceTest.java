package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.dto.ModelActivateRequest;
import com.example.mapchange.common.core.dto.python.InferModelListResponse;
import com.example.mapchange.config.client.AnalysisClient;
import com.example.mapchange.config.client.StorageClient;
import com.example.mapchange.config.domain.ElementCatalogEntity;
import com.example.mapchange.config.domain.ElementCatalogRepository;
import com.example.mapchange.config.domain.ModelRegistryEntity;
import com.example.mapchange.config.domain.ModelRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelRegistryService 激活前置校验测试（对接事项 J-2，bug-2026-09-28 Function-Q3）：
 * 与 infer /infer/models 对账——模型缺失/fp32 缺失拒绝激活；int8 缺失、infer 不可达、
 * 目录映射超类别表降级放行（WARN）。
 */
class ModelRegistryServiceTest {

    private ModelRegistryRepository repository;
    private ConfigService configService;
    private AnalysisClient analysisClient;
    private ElementCatalogRepository elementCatalogRepository;
    private ModelRegistryService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ModelRegistryRepository.class);
        configService = Mockito.mock(ConfigService.class);
        analysisClient = Mockito.mock(AnalysisClient.class);
        elementCatalogRepository = Mockito.mock(ElementCatalogRepository.class);
        service = new ModelRegistryService(repository, configService,
                Mockito.mock(StorageClient.class), analysisClient, elementCatalogRepository,
                "/tmp/models-test");

        ModelRegistryEntity target = new ModelRegistryEntity("landcover-yolo-v11", "v1.0", "SEG",
                "/models/landcover-yolo-v11/v1.0", "tester");
        when(repository.findByModelNameAndModelVersion("landcover-yolo-v11", "v1.0"))
                .thenReturn(Optional.of(target));
        when(repository.findByModelType("SEG")).thenReturn(List.of(target));
        when(elementCatalogRepository.findAll()).thenReturn(List.of(
                new ElementCatalogEntity("forest", "森林", "#228B22", 1, true)));
    }

    private void inferModels(InferModelListResponse resp) {
        when(analysisClient.models()).thenReturn(ApiResponse.ok(resp));
    }

    private InferModelListResponse modelsWith(boolean fp32, boolean int8, Integer classCount) {
        return new InferModelListResponse("/models", List.of(
                new InferModelListResponse.InferModel("landcover-yolo-v11", List.of(
                        new InferModelListResponse.InferModelVersion("v1.0", fp32, int8,
                                classCount != null ? List.of("AnnualCrop") : null, classCount,
                                false, List.of("segmentation"))))));
    }

    @Test
    void activateHappyPath() {
        inferModels(modelsWith(true, true, 11));
        assertDoesNotThrow(() -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService).update(eq("inference"), anyMap(), eq("model-admin"));
    }

    @Test
    void activateRejectedWhenModelMissingInInfer() {
        inferModels(new InferModelListResponse("/models", List.of()));
        assertThrows(BizException.class,
                () -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService, never()).update(anyString(), anyMap(), anyString());
    }

    @Test
    void activateRejectedWhenFp32Missing() {
        inferModels(modelsWith(false, true, 11));
        assertThrows(BizException.class,
                () -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService, never()).update(anyString(), anyMap(), anyString());
    }

    @Test
    void activateProceedsWhenInt8Missing() {
        inferModels(modelsWith(true, false, 11));
        assertDoesNotThrow(() -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService).update(eq("inference"), anyMap(), eq("model-admin"));
    }

    @Test
    void activateProceedsWhenInferUnreachable() {
        when(analysisClient.models()).thenThrow(new RuntimeException("connection refused"));
        assertDoesNotThrow(() -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService).update(eq("inference"), anyMap(), eq("model-admin"));
    }

    @Test
    void activateProceedsWithWarnWhenCatalogOutOfRange() {
        inferModels(modelsWith(true, true, 3));   // 有效 model_class_id 仅 0~1，forest=1 在界内
        when(elementCatalogRepository.findAll()).thenReturn(List.of(
                new ElementCatalogEntity("forest", "森林", "#228B22", 1, true),
                new ElementCatalogEntity("river", "河流", "#1E90FF", 8, true)));   // 8 > 1 超范围 → WARN 放行
        assertDoesNotThrow(() -> service.activate(new ModelActivateRequest("landcover-yolo-v11", "v1.0")));
        verify(configService).update(eq("inference"), anyMap(), eq("model-admin"));
    }

    @Test
    void availableModelsDelegatesToAnalysis() {
        InferModelListResponse resp = modelsWith(true, true, 11);
        inferModels(resp);
        assertDoesNotThrow(() -> service.availableModels());
    }

    @Test
    void availableModelsThrowsWhenInferUnreachable() {
        when(analysisClient.models()).thenThrow(new RuntimeException("connection refused"));
        assertThrows(BizException.class, () -> service.availableModels());
    }
}
