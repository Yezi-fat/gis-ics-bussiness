package com.example.mapchange.result.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.FeatureCollection;
import com.example.mapchange.common.core.dto.RegionsFileRequest;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.python.VectorizeRequest;
import com.example.mapchange.common.core.geo.GeoExtent;
import com.example.mapchange.result.client.PythonComputeClient;
import com.example.mapchange.result.client.StorageClient;
import com.example.mapchange.result.client.TaskClient;
import com.example.mapchange.result.domain.LayerEntity;
import com.example.mapchange.result.domain.LayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 懒矢量化（persist.vectorize=lazy，J-031）：导出/斑块列表请求触发——
 * 已有 regions.geojson 直接返回 key，否则按 layer 表逐蒙版调 compute-service 矢量化并回填。
 * regions.geojson key 规范确定（/{taskId}/regions.geojson，设计 §3.4），回填即文件落位。
 */
@Service
public class LazyVectorizeService {

    private static final Logger log = LoggerFactory.getLogger(LazyVectorizeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LayerRepository layerRepository;
    private final TaskClient taskClient;
    private final StorageClient storageClient;
    private final PythonComputeClient computeClient;
    private final ResultPersistenceService persistenceService;

    public LazyVectorizeService(LayerRepository layerRepository, TaskClient taskClient,
                                StorageClient storageClient, PythonComputeClient computeClient,
                                ResultPersistenceService persistenceService) {
        this.layerRepository = layerRepository;
        this.taskClient = taskClient;
        this.storageClient = storageClient;
        this.computeClient = computeClient;
        this.persistenceService = persistenceService;
    }

    /** 返回 regions.geojson 的 storage key（不存在则矢量化回填） */
    public String ensureVectorized(UUID taskId) {
        String key = "/%s/regions.geojson".formatted(taskId);
        if (existsQuietly(key)) {
            return key;
        }
        log.info("懒矢量化回填: taskId={}", taskId);
        var task = taskClient.get(taskId).data();
        if (task == null) {
            throw new BizException(ErrorCode.INVALID_INPUT, "任务不存在: " + taskId);
        }
        GeoExtent extent = readExtent(task.payload());
        List<LayerEntity> layers = layerRepository.findByTaskId(taskId);
        if (layers.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_INPUT, "任务无图层，无法矢量化: " + taskId);
        }
        ArrayNode features = MAPPER.createArrayNode();
        for (LayerEntity layer : layers) {
            String maskUrl = storageClient.signUrl(
                    new SignUrlRequest(layer.getStorageKey(), "INTERNAL", null)).data().url();
            var resp = computeClient.vectorize(new VectorizeRequest(taskId.toString(), maskUrl, null,
                    layer.getPeriod(), layer.getElement(), layer.getLayerType().name(), extent));
            if (resp.features() != null) {
                resp.features().forEach(features::add);
            }
        }
        java.util.List<com.fasterxml.jackson.databind.JsonNode> featureList = new java.util.ArrayList<>();
        features.forEach(featureList::add);
        return persistenceService.saveRegionsFile(taskId, new FeatureCollection(featureList));
    }

    private boolean existsQuietly(String key) {
        try {
            storageClient.get(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private GeoExtent readExtent(JsonNode payload) {
        try {
            JsonNode node = payload.path("geo_extent");
            if (node.isArray() && node.size() == 4) {
                return new GeoExtent(node.get(0).asDouble(), node.get(1).asDouble(),
                        node.get(2).asDouble(), node.get(3).asDouble());
            }
            return MAPPER.treeToValue(node, GeoExtent.class);
        } catch (Exception e) {
            return null;
        }
    }
}
